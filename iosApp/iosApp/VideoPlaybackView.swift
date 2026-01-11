import Shared
import SwiftUI
import AVKit
import Logging

struct VideoPlaybackView: View {
    let url: String
    let cookieName: String
    let cookieValue: String
    let origin: String
    @ObservedObject var viewModel: SwiftFloatsauceViewModel

    var body: some View {
        if let videoUrl = URL(string: url) {
            VideoPlayerView(url: videoUrl, cookieName: cookieName, cookieValue: cookieValue, origin: origin)
                .edgesIgnoringSafeArea(.all)
        } else {
            Text("Invalid Video URL")
                .foregroundColor(.white)
        }
    }
}

class ResourceLoaderDelegate: NSObject, AVAssetResourceLoaderDelegate {
    let cookieName: String
    let cookieValue: String
    let userAgent: String
    let origin: String
    private var tasks: [AVAssetResourceLoadingRequest: URLSessionDataTask] = [:]

    init(cookieName: String, cookieValue: String, userAgent: String, origin: String) {
        self.cookieName = cookieName
        self.cookieValue = cookieValue
        self.userAgent = userAgent
        self.origin = origin
    }

    func resourceLoader(_ resourceLoader: AVAssetResourceLoader, shouldWaitForLoadingOfRequestedResource loadingRequest: AVAssetResourceLoadingRequest) -> Bool {
        guard let url = loadingRequest.request.url else { return false }
        let urlString = url.absoluteString
        logger.debug("ResourceLoader intercepting: \(urlString)")

        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        components?.scheme = "https"

        guard let httpsUrl = components?.url else { return false }

        var request = URLRequest(url: httpsUrl)
        request.addValue("\(cookieName)=\(cookieValue)", forHTTPHeaderField: "Cookie")
        request.addValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.addValue(origin, forHTTPHeaderField: "Origin")

        let task = URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                guard let self = self else { return }
                defer { self.tasks.removeValue(forKey: loadingRequest) }

                if let error = error {
                    if (error as NSError).code != NSURLErrorCancelled {
                        logger.debug("ResourceLoader error: \(error.localizedDescription) for \(urlString)")
                    }
                    loadingRequest.finishLoading(with: error)
                    return
                }

                guard let data = data, let httpResponse = response as? HTTPURLResponse else {
                    logger.debug("ResourceLoader invalid response for \(urlString)")
                    loadingRequest.finishLoading(with: NSError(domain: "ResourceLoader", code: -1, userInfo: nil))
                    return
                }

                let mimeType = httpResponse.mimeType?.lowercased() ?? ""
                let isManifest = httpsUrl.pathExtension == "m3u8" || mimeType.contains("mpegurl")
                
                logger.debug("ResourceLoader response \(httpResponse.statusCode) [\(data.count) bytes, \(mimeType)] for \(urlString)")

                if let contentInformationRequest = loadingRequest.contentInformationRequest {
                    contentInformationRequest.contentType = isManifest ? "com.apple.mpegurl" : mimeType
                    contentInformationRequest.contentLength = httpResponse.expectedContentLength != -1 ? httpResponse.expectedContentLength : Int64(data.count)
                    contentInformationRequest.isByteRangeAccessSupported = true
                }

                loadingRequest.response = httpResponse

                if isManifest {
                    if let manifestStr = String(data: data, encoding: .utf8) {
                        let modifiedManifest = self.rewriteManifest(manifestStr, baseUrl: httpsUrl)
                        if let modifiedData = modifiedManifest.data(using: .utf8) {
                            if let contentInformationRequest = loadingRequest.contentInformationRequest {
                                contentInformationRequest.contentLength = Int64(modifiedData.count)
                            }
                            logger.debug("ResourceLoader modified manifest [\(modifiedData.count) bytes] for \(urlString)")
                            loadingRequest.dataRequest?.respond(with: modifiedData)
                            loadingRequest.finishLoading()
                            return
                        }
                    }
                }

                loadingRequest.dataRequest?.respond(with: data)
                loadingRequest.finishLoading()
            }
        }
        
        tasks[loadingRequest] = task
        task.resume()

        return true
    }

    private func rewriteManifest(_ manifest: String, baseUrl: URL) -> String {
        let lines = manifest.components(separatedBy: .newlines)
        var processedLines = [String]()
        var lastLineWasStreamInf = false

        for line in lines {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty {
                processedLines.append(line)
                continue
            }

            if trimmed.hasPrefix("#EXT-X-KEY") || trimmed.hasPrefix("#EXT-X-SESSION-KEY") || trimmed.hasPrefix("#EXT-X-I-FRAME-STREAM-INF") || trimmed.hasPrefix("#EXT-X-MEDIA") {
                processedLines.append(rewriteAttributeUri(line, scheme: "floatsauce", baseUrl: baseUrl))
                lastLineWasStreamInf = false
            } else if trimmed.hasPrefix("#EXT-X-MAP") {
                processedLines.append(rewriteAttributeUri(line, scheme: "https", baseUrl: baseUrl))
                lastLineWasStreamInf = false
            } else if trimmed.hasPrefix("#EXT-X-STREAM-INF") {
                processedLines.append(line)
                lastLineWasStreamInf = true
            } else if trimmed.hasPrefix("#") {
                processedLines.append(line)
                lastLineWasStreamInf = false
            } else {
                // URI line
                let scheme = lastLineWasStreamInf ? "floatsauce" : "https"
                processedLines.append(absoluteUri(trimmed, scheme: scheme, baseUrl: baseUrl))
                lastLineWasStreamInf = false
            }
        }
        return processedLines.joined(separator: "\n")
    }

    private func rewriteAttributeUri(_ line: String, scheme: String, baseUrl: URL) -> String {
        guard let uriRange = line.range(of: "URI=\"") else { return line }
        let afterUri = line[uriRange.upperBound...]
        guard let endQuoteRange = afterUri.range(of: "\"") else { return line }
        let uri = String(afterUri[..<endQuoteRange.lowerBound])
        let absolute = absoluteUri(uri, scheme: scheme, baseUrl: baseUrl)
        return line.replacingOccurrences(of: "URI=\"\(uri)\"", with: "URI=\"\(absolute)\"")
    }

    private func absoluteUri(_ uri: String, scheme: String, baseUrl: URL) -> String {
        let absoluteUrl: URL
        if let url = URL(string: uri), url.scheme != nil {
            absoluteUrl = url
        } else if let url = URL(string: uri, relativeTo: baseUrl) {
            absoluteUrl = url.absoluteURL
        } else {
            return uri
        }
        
        var components = URLComponents(url: absoluteUrl, resolvingAgainstBaseURL: false)
        components?.scheme = scheme
        return components?.url?.absoluteString ?? absoluteUrl.absoluteString
    }

    func resourceLoader(_ resourceLoader: AVAssetResourceLoader, didCancel loadingRequest: AVAssetResourceLoadingRequest) {
        if let task = tasks.removeValue(forKey: loadingRequest) {
            logger.debug("ResourceLoader cancelled request for: \(loadingRequest.request.url?.absoluteString ?? "unknown")")
            task.cancel()
        }
    }
}

struct VideoPlayerView: UIViewControllerRepresentable {
    let url: URL
    let cookieName: String
    let cookieValue: String
    let origin: String

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        logger.debug("tvOS VideoPlayerView: playing \(url.absoluteString)")

        let userAgent = PlatformKt.getPlatform().userAgent
        logger.debug("tvOS VideoPlayerView User-Agent: \(userAgent)")

        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        components?.scheme = "floatsauce"
        let customUrl = components?.url ?? url

        let asset = AVURLAsset(url: customUrl)
        let delegate = ResourceLoaderDelegate(cookieName: cookieName, cookieValue: cookieValue, userAgent: userAgent, origin: origin)
        context.coordinator.delegate = delegate
        asset.resourceLoader.setDelegate(delegate, queue: DispatchQueue.main)

        let playerItem = AVPlayerItem(asset: asset)
        let player = AVPlayer(playerItem: playerItem)

        let controller = AVPlayerViewController()
        controller.player = player
        player.play()
        return controller
    }

    func updateUIViewController(_ uiViewController: AVPlayerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    class Coordinator {
        var delegate: ResourceLoaderDelegate?
    }
}
