import Shared
import SwiftUI

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

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
