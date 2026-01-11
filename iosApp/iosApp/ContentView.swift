import Shared
import SwiftUI
import AVKit
import Logging

let logger: Logger = {
    var logger = Logger(label: "org.miker.floatsauce")
    logger.logLevel = .debug
    return logger
}()

class SwiftFloatsauceViewModel: ObservableObject {
    @Published var currentScreen: Screen = Screen.ServiceSelection()
    @Published var services: [AuthService] = []
    @Published var subscriptions: [Creator] = []
    @Published var videos: [Video] = []
    @Published var authState: AuthState? = nil
    
    private let viewModel: FloatsauceViewModel
    
    init() {
        self.viewModel = FloatsauceViewModel(repository: FloatsauceRepositoryImpl(secureStorage: AppleSecureStorage()))
        self.currentScreen = viewModel.currentScreen.value as! Screen
        
        viewModel.watchCurrentScreen { [weak self] screen in
            DispatchQueue.main.async {
                self?.currentScreen = screen
            }
        }
        
        viewModel.watchServices { [weak self] services in
            DispatchQueue.main.async {
                self?.services = services
            }
        }
        
        viewModel.watchSubscriptions { [weak self] subscriptions in
            DispatchQueue.main.async {
                self?.subscriptions = subscriptions
            }
        }
        
        viewModel.watchVideos { [weak self] videos in
            DispatchQueue.main.async {
                self?.videos = videos
            }
        }
        
        viewModel.watchAuthState { [weak self] authState in
            DispatchQueue.main.async {
                self?.authState = authState
            }
        }
    }
    
    func selectService(service: AuthService) {
        viewModel.selectService(service: service)
    }
    
    func selectCreator(creator: Creator) {
        viewModel.selectCreator(creator: creator)
    }
    
    func playVideo(video: Video, creator: Creator) {
        viewModel.playVideo(video: video, creator: creator)
    }
    
    func fetchCreatorDetails(creator: Creator) {
        viewModel.fetchCreatorDetails(creator: creator)
    }
    
    func goBack() {
        DispatchQueue.main.async {
            self.viewModel.goBack()
        }
    }
}

struct ContentView: View {
    @StateObject private var viewModel = SwiftFloatsauceViewModel()
    
    var body: some View {
        ZStack {
            if viewModel.currentScreen is Screen.ServiceSelection {
                ServiceSelectionView(viewModel: viewModel)
            } else if let qrScreen = viewModel.currentScreen as? Screen.QRLogin {
                QRLoginView(service: qrScreen.service, viewModel: viewModel)
                    .onExitCommand(perform: viewModel.goBack)
            } else if let failedScreen = viewModel.currentScreen as? Screen.AuthFailed {
                AuthFailedView(service: failedScreen.service, viewModel: viewModel)
                    .onExitCommand(perform: viewModel.goBack)
            } else if let subScreen = viewModel.currentScreen as? Screen.Subscriptions {
                SubscriptionsView(service: subScreen.service, viewModel: viewModel)
                    .onExitCommand(perform: viewModel.goBack)
            } else if let creatorScreen = viewModel.currentScreen as? Screen.CreatorDetail {
                CreatorDetailView(creator: creatorScreen.creator, viewModel: viewModel)
                    .onExitCommand(perform: viewModel.goBack)
            } else if let playbackScreen = viewModel.currentScreen as? Screen.VideoPlayback {
                VideoPlaybackView(url: playbackScreen.url, cookieName: playbackScreen.cookieName, cookieValue: playbackScreen.cookieValue, origin: playbackScreen.origin, viewModel: viewModel)
                    .onExitCommand(perform: viewModel.goBack)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
        .edgesIgnoringSafeArea(.all)
    }
}

struct ServiceSelectionView: View {
    @ObservedObject var viewModel: SwiftFloatsauceViewModel
    var body: some View {
        VStack(spacing: 40) {
            Text("Choose Service")
                .font(.largeTitle)
                .foregroundColor(.white)
            HStack(spacing: 40) {
                ForEach(viewModel.services, id: \.self) { service in
                    Button(action: { viewModel.selectService(service: service) }) {
                        Text(service.displayName)
                            .frame(width: 300, height: 150)
                    }
                }
            }
        }
    }
}

struct QRLoginView: View {
    let service: AuthService
    @ObservedObject var viewModel: SwiftFloatsauceViewModel
    var body: some View {
        VStack(spacing: 30) {
            Text("Login to \(service.displayName)")
                .font(.largeTitle)
                .foregroundColor(.white)
            Text("Scan the QR code on your phone")
                .foregroundColor(.gray)
            
            Rectangle()
                .fill(Color.white)
                .frame(width: 200, height: 200)
                .overlay(
                    Text("QR CODE\n\(viewModel.authState?.qrCodeUrl ?? "")")
                        .foregroundColor(.black)
                        .multilineTextAlignment(.center)
                )
            
            Button("Back") { viewModel.goBack() }
        }
    }
}

struct AuthFailedView: View {
    let service: AuthService
    @ObservedObject var viewModel: SwiftFloatsauceViewModel
    var body: some View {
        VStack(spacing: 30) {
            Text("Authorization failed")
                .font(.largeTitle)
                .foregroundColor(.white)
            
            Button("Try again?") {
                viewModel.selectService(service: service)
            }
            
            Button("Back") { viewModel.goBack() }
        }
    }
}

struct SubscriptionsView: View {
    let service: AuthService
    @ObservedObject var viewModel: SwiftFloatsauceViewModel
    
    private var bannerName: String {
        switch service {
        case .floatplane: return "floatplane"
        case .saucePlus: return "sauceplus"
        default: return ""
        }
    }

    private var siteName: String {
        switch service {
        case .floatplane: return "floatplane.com"
        case .saucePlus: return "sauceplus.com"
        default: return ""
        }
    }
    
    let columns = [
        GridItem(.flexible()),
        GridItem(.flexible()),
        GridItem(.flexible()),
        GridItem(.flexible())
    ]
    
    var body: some View {
        VStack(spacing: 0) {
            if !bannerName.isEmpty {
                Image(bannerName)
                    .resizable()
                    .aspectRatio(3840/720, contentMode: .fit)
                    .frame(maxWidth: .infinity)
            }
            
            if viewModel.subscriptions.isEmpty {
                VStack(spacing: 20) {
                    Spacer()
                    Text("No subscriptions found. Please add subscriptions at \(siteName)")
                        .foregroundColor(.white)
                    Button("Back") {
                        viewModel.goBack()
                    }
                    Spacer()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 60) {
                        ForEach(viewModel.subscriptions, id: \.id) { creator in
                            CreatorCard(creator: creator, viewModel: viewModel) {
                                viewModel.selectCreator(creator: creator)
                            }
                        }
                    }
                    .padding(60)
                }
            }
        }
    }
}

struct CreatorCard: View {
    let creator: Creator
    @ObservedObject var viewModel: SwiftFloatsauceViewModel
    let onClick: () -> Void
    @FocusState private var isFocused: Bool
    
    var body: some View {
        Button(action: onClick) {
            if let iconUrl = creator.iconUrl, let url = URL(string: iconUrl) {
                AsyncImage(url: url) { image in
                    image.resizable()
                } placeholder: {
                    Circle().fill(Color.gray)
                }
                .aspectRatio(contentMode: .fill)
                .frame(width: 400, height: 400)
                .clipShape(Circle())
                .hoverEffect(.highlight)
            } else {
                Circle()
                    .fill(Color.gray)
                    .frame(width: 400, height: 400)
            }
            Text(creator.name)
        }
        .onAppear {
            viewModel.fetchCreatorDetails(creator: creator)
        }
        .buttonStyle(.borderless)
    }
}

struct CreatorDetailView: View {
    let creator: Creator
    @ObservedObject var viewModel: SwiftFloatsauceViewModel
    var body: some View {
        VStack {
            HStack {
                Button("Back") { viewModel.goBack() }
                Spacer()
                Text(creator.name)
                    .font(.title)
                    .foregroundColor(.white)
                Spacer()
            }
            .padding()
            
            List(viewModel.videos, id: \.id) { video in
                Button(action: { viewModel.playVideo(video: video, creator: creator) }) {
                    VStack(alignment: .leading) {
                        Text(video.title)
                            .foregroundColor(.white)
                        Text(video.duration)
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                }
            }
        }
    }
}

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

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
