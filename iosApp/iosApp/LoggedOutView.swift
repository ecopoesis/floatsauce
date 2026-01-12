import SwiftUI
import Shared

struct LoggedOutView: View {
    let service: AuthService
    let viewModel: SwiftFloatsauceViewModel

    var body: some View {
        VStack(spacing: 32) {
            let serviceName = service == .floatplane ? "Floatplane" : "Sauce+"
            
            Text("You have been logged out of \(serviceName)")
                .font(.title)
                .foregroundColor(.white)
            
            Button(action: {
                viewModel.selectService(service: service)
            }) {
                Text("Login to \(serviceName)")
                    .font(.headline)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
    }
}
