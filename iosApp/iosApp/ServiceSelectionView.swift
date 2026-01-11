import Shared
import SwiftUI

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
