import Shared
import SwiftUI

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
