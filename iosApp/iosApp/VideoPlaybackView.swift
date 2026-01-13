import Shared
import SwiftUI
import AVKit
import Logging

struct VideoPlaybackView: View {
    let video: Video
    let url: String
    let resumeProgressSeconds: Int32
    let cookieName: String
    let cookieValue: String
    let origin: String
    let thumbnailUrl: String?
    let thumbnailWidth: Int32
    let thumbnailHeight: Int32
    let thumbnailFrameCount: Int32
    @ObservedObject var viewModel: SwiftFloatsauceViewModel

    var body: some View {
        if let videoUrl = URL(string: url) {
            VideoPlayerView(
                video: video,
                url: videoUrl,
                resumeProgressSeconds: resumeProgressSeconds,
                cookieName: cookieName,
                cookieValue: cookieValue,
                origin: origin,
                thumbnailUrl: thumbnailUrl,
                thumbnailWidth: thumbnailWidth,
                thumbnailHeight: thumbnailHeight,
                thumbnailFrameCount: thumbnailFrameCount,
                viewModel: viewModel
            )
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
    let video: Video
    let url: URL
    let resumeProgressSeconds: Int32
    let cookieName: String
    let cookieValue: String
    let origin: String
    let thumbnailUrl: String?
    let thumbnailWidth: Int32
    let thumbnailHeight: Int32
    let thumbnailFrameCount: Int32
    let viewModel: SwiftFloatsauceViewModel

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        logger.debug("tvOS VideoPlayerView: playing \(url.absoluteString) (resume at \(resumeProgressSeconds))")

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

        if resumeProgressSeconds > 0 {
            player.seek(to: CMTime(seconds: Double(resumeProgressSeconds), preferredTimescale: 1))
        }

        context.coordinator.setupObserver(player: player, video: video, viewModel: viewModel)

        let controller = AVPlayerViewController()
        controller.player = player
        player.play()

        let overlay = UIHostingController(rootView: ThumbnailOverlay(
            thumbnailUrl: thumbnailUrl,
            thumbnailWidth: thumbnailWidth,
            thumbnailHeight: thumbnailHeight,
            thumbnailFrameCount: thumbnailFrameCount,
            coordinator: context.coordinator
        ))
        overlay.view.backgroundColor = .clear
        if let contentOverlay = controller.contentOverlayView {
            contentOverlay.addSubview(overlay.view)
            overlay.view.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                overlay.view.topAnchor.constraint(equalTo: contentOverlay.topAnchor),
                overlay.view.leadingAnchor.constraint(equalTo: contentOverlay.leadingAnchor),
                overlay.view.trailingAnchor.constraint(equalTo: contentOverlay.trailingAnchor),
                overlay.view.bottomAnchor.constraint(equalTo: contentOverlay.bottomAnchor)
            ])
        }

        return controller
    }

    func updateUIViewController(_ uiViewController: AVPlayerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    class Coordinator: NSObject, ObservableObject {
        var delegate: ResourceLoaderDelegate?
        var player: AVPlayer?
        var video: Video?
        var viewModel: SwiftFloatsauceViewModel?
        var timeObserver: Any?
        var lastSentProgress: Int = -1
        var lastUpdateTime: Date = Date.distantPast

        @Published var isScrubbing: Bool = false
        @Published var scrubbingTime: Double = 0
        @Published var duration: Double = 0
        private var lastScrubTime: Date = Date.distantPast

        private var durationObserver: NSKeyValueObservation?

        func setupObserver(player: AVPlayer, video: Video, viewModel: SwiftFloatsauceViewModel) {
            self.player = player
            self.video = video
            self.viewModel = viewModel

            let interval = CMTime(seconds: 0.5, preferredTimescale: CMTimeScale(NSEC_PER_SEC))
            timeObserver = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
                self?.handleTimeUpdate(time: time)
            }

            player.addObserver(self, forKeyPath: "timeControlStatus", options: [.old, .new], context: nil)

            if let item = player.currentItem {
                setupItemObservers(item: item)
            }

            NotificationCenter.default.addObserver(self, selector: #selector(playerDidFinishPlaying), name: .AVPlayerItemDidPlayToEndTime, object: player.currentItem)
            NotificationCenter.default.addObserver(self, selector: #selector(timeJumped), name: .AVPlayerItemTimeJumped, object: player.currentItem)
        }

        func setupItemObservers(item: AVPlayerItem) {
            durationObserver = item.observe(\.duration, options: [.new]) { [weak self] item, change in
                DispatchQueue.main.async {
                    if item.duration.isNumeric {
                        self?.duration = item.duration.seconds
                    }
                }
            }
        }

        @objc func timeJumped() {
            DispatchQueue.main.async {
                self.isScrubbing = true
                self.lastScrubTime = Date()
                if let time = self.player?.currentTime() {
                    self.scrubbingTime = time.seconds
                }
            }
        }

        override func observeValue(forKeyPath keyPath: String?, of object: Any?, change: [NSKeyValueChangeKey : Any]?, context: UnsafeMutableRawPointer?) {
            if keyPath == "timeControlStatus" {
                if let player = object as? AVPlayer {
                    if player.timeControlStatus == .paused {
                        logger.debug("Video paused, sending immediate progress update")
                        sendProgressUpdate(force: true)
                    }
                }
            }
        }

        @objc func playerDidFinishPlaying() {
            guard let duration = player?.currentItem?.duration, duration.isNumeric else { return }
            let durationSeconds = Int(duration.seconds)
            logger.debug("Video finished, sending progress update: \(durationSeconds) seconds")
            sendProgressUpdate(progress: durationSeconds, force: true)
        }

        func handleTimeUpdate(time: CMTime) {
            guard let player = player else { return }
            if isScrubbing {
                if Date().timeIntervalSince(lastScrubTime) > 2.0 {
                    isScrubbing = false
                } else {
                    scrubbingTime = time.seconds
                }
            }
            if player.timeControlStatus == .playing {
                isScrubbing = false
                let now = Date()
                if now.timeIntervalSince(lastUpdateTime) >= 10 {
                    sendProgressUpdate()
                }
            }
        }

        func sendProgressUpdate(progress: Int? = nil, force: Bool = false) {
            guard let player = player, let video = video, let viewModel = viewModel else { return }

            let currentProgress: Int
            if let p = progress {
                currentProgress = p
            } else {
                let time = player.currentTime()
                guard time.isNumeric else { return }
                currentProgress = Int(time.seconds)
            }

            if force || (currentProgress != lastSentProgress && currentProgress >= 0) {
                logger.debug("Sending progress update: \(currentProgress) seconds for video \(video.id)")
                viewModel.updateVideoProgress(video: video, progressSeconds: Int32(currentProgress))
                lastSentProgress = currentProgress
                lastUpdateTime = Date()
            }
        }

        deinit {
            if let observer = timeObserver {
                player?.removeTimeObserver(observer)
            }
            player?.removeObserver(self, forKeyPath: "timeControlStatus")
            durationObserver?.invalidate()
            NotificationCenter.default.removeObserver(self)
        }
    }
}

struct ThumbnailOverlay: View {
    let thumbnailUrl: String?
    let thumbnailWidth: Int32
    let thumbnailHeight: Int32
    let thumbnailFrameCount: Int32
    @ObservedObject var coordinator: VideoPlayerView.Coordinator

    var body: some View {
        GeometryReader { geometry in
            if coordinator.isScrubbing, let urlStr = thumbnailUrl, let url = URL(string: urlStr), thumbnailFrameCount > 0, coordinator.duration > 0 {
                let progress = coordinator.scrubbingTime / coordinator.duration
                let thumbHeight: CGFloat = 200
                let thumbWidth = thumbHeight * (160.0 / 90.0)

                let centerX = geometry.size.width * CGFloat(progress)
                let xOffset = min(max(centerX - thumbWidth / 2, 0), geometry.size.width - thumbWidth)

                let framesPerRow = Int(thumbnailWidth) / 160
                if framesPerRow > 0 {
                    let frameIndex = min(max(Int(Double(thumbnailFrameCount) * progress), 0), Int(thumbnailFrameCount) - 1)
                    let column = frameIndex % framesPerRow
                    let row = frameIndex / framesPerRow
                    
                    let scale = thumbHeight / 90.0
                    let fullSpriteWidth = CGFloat(thumbnailWidth) * scale
                    let fullSpriteHeight = CGFloat(thumbnailHeight) * scale

                    VStack {
                        Spacer()
                        ZStack(alignment: .topLeading) {
                            AsyncImage(url: url) { phase in
                                switch phase {
                                case .success(let image):
                                    image.resizable()
                                        .frame(width: fullSpriteWidth, height: fullSpriteHeight)
                                        .offset(x: -thumbWidth * CGFloat(column), y: -thumbHeight * CGFloat(row))
                                default:
                                    Color.black
                                }
                            }
                        }
                        .frame(width: thumbWidth, height: thumbHeight)
                        .clipped()
                        .offset(x: xOffset)
                        .padding(.bottom, 150)
                    }
                }
            }
        }
    }
}
