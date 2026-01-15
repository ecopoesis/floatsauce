import Shared
import SwiftUI

class SwiftFloatsauceViewModel: ObservableObject {
    @Published var currentScreen: Screen = Screen.ServiceSelection()
    @Published var services: [AuthService] = []
    @Published var subscriptions: [Creator] = []
    @Published var browseCreators: [Creator] = []
    @Published var videos: [Video] = []
    @Published var selectedChannel: Channel? = nil
    @Published var lastPlayedVideoId: String? = nil
    @Published var authState: AuthState? = nil
    @Published var loginStatuses: [AuthService: Bool] = [:]
    
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
        
        viewModel.watchBrowseCreators { [weak self] browseCreators in
            DispatchQueue.main.async {
                self?.browseCreators = browseCreators
            }
        }
        
        viewModel.watchVideos { [weak self] videos in
            DispatchQueue.main.async {
                self?.videos = videos
            }
        }
        
        viewModel.watchSelectedChannel { [weak self] selectedChannel in
            DispatchQueue.main.async {
                self?.selectedChannel = selectedChannel
            }
        }
        
        viewModel.watchLastPlayedVideoId { [weak self] lastPlayedVideoId in
            DispatchQueue.main.async {
                self?.lastPlayedVideoId = lastPlayedVideoId
            }
        }
        
        viewModel.watchAuthState { [weak self] authState in
            DispatchQueue.main.async {
                self?.authState = authState
            }
        }
        
        viewModel.watchLoginStatuses { [weak self] loginStatuses in
            DispatchQueue.main.async {
                var statuses: [AuthService: Bool] = [:]
                for (key, value) in loginStatuses {
                    if let service = key as? AuthService {
                        statuses[service] = (value as? Bool) ?? (value as? NSNumber)?.boolValue ?? false
                    }
                }
                self?.loginStatuses = statuses
            }
        }
    }
    
    func selectService(service: AuthService) {
        viewModel.selectService(service: service)
    }
    
    func selectCreator(creator: Creator) {
        viewModel.selectCreator(creator: creator)
    }
    
    func selectChannel(creator: Creator, channel: Channel?) {
        viewModel.selectChannel(creator: creator, channel: channel)
    }
    
    func loadMoreVideos(creator: Creator) {
        viewModel.loadMoreVideos(creator: creator)
    }
    
    func playVideo(video: Video, creator: Creator) {
        viewModel.playVideo(video: video, creator: creator)
    }

    func updateVideoProgress(video: Video, progressSeconds: Int32, progressPercent: Int32? = nil) {
        viewModel.updateVideoProgress(video: video, progressSeconds: progressSeconds, progressPercent: progressPercent != nil ? KotlinInt(value: progressPercent!) : nil)
    }

    func fetchCreatorDetails(creator: Creator) {
        viewModel.fetchCreatorDetails(creator: creator)
    }
    
    func logout(service: AuthService) {
        viewModel.logout(service: service)
    }
    
    func goBack() {
        DispatchQueue.main.async {
            self.viewModel.goBack()
        }
    }
}
