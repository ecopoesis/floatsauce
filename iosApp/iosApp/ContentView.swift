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
                    LazyVGrid(columns: columns, spacing: 16) {
                        ForEach(viewModel.subscriptions, id: \.id) { creator in
                            CreatorCard(creator: creator, viewModel: viewModel) {
                                viewModel.selectCreator(creator: creator)
                            }
                        }
                    }
                    .padding(16)
                }
            }
        }
    }
}

struct CreatorCard: View {
    let creator: Creator
    @ObservedObject var viewModel: SwiftFloatsauceViewModel
    let onClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 12) {
                if let iconUrl = creator.iconUrl, let url = URL(string: iconUrl) {
                    AsyncImage(url: url) { image in
                        image.resizable()
                    } placeholder: {
                        Color.gray
                    }
                    .frame(width: 80, height: 80)
                    .clipShape(Circle())
                } else {
                    Circle()
                        .fill(Color.gray)
                        .frame(width: 80, height: 80)
                }
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(creator.name)
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .lineLimit(1)
                    
                    if let channels = creator.channels, channels.intValue > 1 {
                        Text("\(channels.intValue) Channels")
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                }
                Spacer()
            }
            .padding(12)
            .background(Color(white: 30.0/255.0)) // 0xFF1E1E1E
            .cornerRadius(8)
        }
        .buttonStyle(PlainButtonStyle())
        .onAppear {
            viewModel.fetchCreatorDetails(creator: creator)
        }
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
                Button(action: { print("Playing: \(video.videoUrl)") }) {
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

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
