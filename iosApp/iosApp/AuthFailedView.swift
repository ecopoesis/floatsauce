import Shared
import SwiftUI

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
