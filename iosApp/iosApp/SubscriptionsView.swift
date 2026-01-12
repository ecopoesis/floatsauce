import Shared
import SwiftUI

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
    
    private var browseCreators: [Creator] {
        viewModel.browseCreators.filter { creator in
            !viewModel.subscriptions.contains(where: { $0.id == creator.id })
        }
    }
    
    var body: some View {
        if viewModel.subscriptions.isEmpty && browseCreators.isEmpty {
            VStack(spacing: 0) {
                if !bannerName.isEmpty {
                    Image(bannerName)
                        .resizable()
                        .aspectRatio(3840/720, contentMode: .fit)
                        .frame(maxWidth: .infinity)
                }
                
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
            }
        } else {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    if !bannerName.isEmpty {
                        Image(bannerName)
                            .resizable()
                            .aspectRatio(3840/720, contentMode: .fit)
                            .frame(maxWidth: .infinity)
                    }
                    
                    if !viewModel.subscriptions.isEmpty {
                        Text("Your subscriptions")
                            .font(.headline)
                            .padding(.horizontal, 60)
                            .padding(.top, 40)
                        
                        LazyVGrid(columns: columns, spacing: 60) {
                            ForEach(viewModel.subscriptions, id: \.id) { creator in
                                CreatorCard(creator: creator, viewModel: viewModel) {
                                    viewModel.selectCreator(creator: creator)
                                }
                            }
                        }
                        .padding(.horizontal, 60)
                        .padding(.top, 20)
                    }
                    
                    if !browseCreators.isEmpty {
                        Text("Browse creators")
                            .font(.headline)
                            .padding(.horizontal, 60)
                            .padding(.top, 40)
                        
                        LazyVGrid(columns: columns, spacing: 60) {
                            ForEach(browseCreators, id: \.id) { creator in
                                CreatorCard(creator: creator, viewModel: viewModel) {
                                    viewModel.selectCreator(creator: creator)
                                }
                            }
                        }
                        .padding(.horizontal, 60)
                        .padding(.top, 20)
                    }
                }
                .padding(.bottom, 60)
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
                .scaleEffect(isFocused ? 1.1 : 1.0)
                .animation(.easeInOut(duration: 0.2), value: isFocused)
        }
        .onAppear {
            viewModel.fetchCreatorDetails(creator: creator)
        }
        .buttonStyle(.borderless)
        .focused($isFocused)
    }
}
