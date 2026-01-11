import Shared
import SwiftUI

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
