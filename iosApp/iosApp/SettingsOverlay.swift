import SwiftUI
import Shared

struct SettingsButton: View {
    let action: () -> Void
    @FocusState private var isFocused: Bool

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: "gearshape.fill")
                    .frame(width: 40, height: 40)
                    .clipShape(Circle())

                Text("Settings")
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
        .focused($isFocused)
        .glassEffect(.regular)
    }
}

struct SettingsOverlay: View {
    @Binding var isPresented: Bool
    let viewModel: SwiftFloatsauceViewModel
    @FocusState private var focusedButton: Int?
    @Namespace var namespace

    var body: some View {
        if isPresented {
            ZStack {
                HStack {
                    Spacer()
                    VStack(alignment: .leading, spacing: 24) {
                        Text("Settings")
                            .font(.title3)
                            .padding(.bottom, 20)

                        let floatplaneLoggedIn = viewModel.loginStatuses[.floatplane] ?? false
                        Button(action: {
                            if floatplaneLoggedIn {
                                viewModel.logout(service: .floatplane)
                            } else {
                                viewModel.selectService(service: .floatplane)
                            }
                            isPresented = false
                        }) {
                            Text(floatplaneLoggedIn ? "Logout of Floatplane" : "Login to Floatplane")
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .focused($focusedButton, equals: 0)
                        .glassEffect(.regular)

                        let saucePlusLoggedIn = viewModel.loginStatuses[.saucePlus] ?? false
                        Button(action: {
                            if saucePlusLoggedIn {
                                viewModel.logout(service: .saucePlus)
                            } else {
                                viewModel.selectService(service: .saucePlus)
                            }
                            isPresented = false
                        }) {
                            Text(saucePlusLoggedIn ? "Logout of Sauce+" : "Login to Sauce+")
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .focused($focusedButton, equals: 1)
                        .glassEffect(.regular)
                    }
                    .focusScope(namespace)
                    .frame(width: 500)
                    .padding(50)
                    .glassEffect(in: RoundedRectangle(cornerRadius: 30))
                    .buttonStyle(.glass)
                }
            }
            .padding(50)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .transition(.move(edge: .trailing))
            .zIndex(100)
            .onExitCommand {
                isPresented = false
            }
            .onAppear {
                focusedButton = 0
            }
        }
    }
}
