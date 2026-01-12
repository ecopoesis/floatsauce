import Shared
import SwiftUI

struct CreatorDetailView: View {
    let creator: Creator
    @ObservedObject var viewModel: SwiftFloatsauceViewModel
    
    private var currentCreator: Creator {
        viewModel.subscriptions.first(where: { $0.id == creator.id }) ?? creator
    }

    let columns = [
        GridItem(.flexible(), spacing: 40),
        GridItem(.flexible(), spacing: 40),
        GridItem(.flexible(), spacing: 40)
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // Banner
                if let bannerUrl = currentCreator.bannerUrl, let url = URL(string: bannerUrl) {
                    AsyncImage(url: url) { image in
                        image.resizable()
                            .aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Color.gray
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 400) // Top third roughly
                    .clipped()
                } else {
                    Color.black.frame(height: 200)
                }
                
                VStack(alignment: .leading, spacing: 40) {
                    Text(currentCreator.name)
                        .font(.largeTitle)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .padding(.horizontal, 60)
                        .padding(.top, 40)
                    
                    if let channels = currentCreator.channels, channels.count > 1 {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 30) {
                                ChannelButton(title: "All", iconUrl: currentCreator.iconUrl, isSelected: viewModel.selectedChannel == nil) {
                                    viewModel.selectChannel(creator: currentCreator, channel: nil)
                                }
                                
                                ForEach(channels, id: \.id) { channel in
                                    ChannelButton(title: channel.title, iconUrl: channel.iconUrl, isSelected: viewModel.selectedChannel?.id == channel.id) {
                                        viewModel.selectChannel(creator: currentCreator, channel: channel)
                                    }
                                }
                            }
                            .padding(.horizontal, 60)
                            .padding(.vertical, 10)
                        }
                    }

                    if viewModel.videos.isEmpty {
                        HStack {
                            Spacer()
                            Button("No videos found") {
                                viewModel.goBack()
                            }
                            Spacer()
                        }
                        .padding(.top, 100)
                    } else {
                        LazyVGrid(columns: columns, spacing: 60) {
                            ForEach(viewModel.videos, id: \.id) { video in
                                VideoCard(video: video, creator: currentCreator, viewModel: viewModel)
                            }
                        }
                        .padding(.horizontal, 60)
                        .padding(.bottom, 60)
                    }
                }
            }
        }
        .edgesIgnoringSafeArea(.all)
        .onAppear {
            viewModel.fetchCreatorDetails(creator: creator)
        }
    }
}

struct ChannelButton: View {
    let title: String
    let iconUrl: String?
    let isSelected: Bool
    let action: () -> Void
    
    @FocusState private var isFocused: Bool
    
    private let highlightColor = Color(red: 115.0/255.0, green: 55.0/255.0, blue: 181.0/255.0)
    private let focusedScale: CGFloat = 1.1

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                if let iconUrl = iconUrl, let url = URL(string: iconUrl) {
                    AsyncImage(url: url) { image in
                        image.resizable()
                            .aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Circle().fill(Color.gray)
                    }
                    .frame(width: 40, height: 40)
                    .clipShape(Circle())

                } else {
                    Circle().fill(Color.gray)
                        .frame(width: 40, height: 40)
                }

                Text(title)
                    .font(.headline)
                    .foregroundColor(.white)
            }
            .padding(.leading, 10)
            .padding(.trailing, 24)
            .padding(.vertical, 10)
            .background(
                isSelected ? Color.white.opacity(0.3) : Color.black.opacity(0.3)
            )
            .clipShape(Capsule())
            .overlay(
                Capsule()
                    .stroke(isFocused ? highlightColor : Color.white, lineWidth: isFocused ? 4 : 1)
            )
        }
        .buttonStyle(.borderless)
        .focused($isFocused)
        .scaleEffect(isFocused ? focusedScale : 1.0)
        .animation(.easeInOut(duration: 0.2), value: isFocused)
    }
}

struct VideoCard: View {
    let video: Video
    let creator: Creator
    @ObservedObject var viewModel: SwiftFloatsauceViewModel
    @FocusState private var isFocused: Bool
    
    var body: some View {
        Button(action: { viewModel.playVideo(video: video, creator: creator) }) {
            VStack(alignment: .leading, spacing: 15) {
                // Thumbnail
                ZStack(alignment: .bottomTrailing) {
                    if let thumbnailUrl = video.thumbnailUrl, let url = URL(string: thumbnailUrl) {
                        AsyncImage(url: url) { image in
                            image.resizable()
                                .aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Rectangle().fill(Color.gray)
                        }
                        .frame(maxWidth: .infinity)
                        .aspectRatio(16/9, contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    } else {
                        Rectangle()
                            .fill(Color.gray)
                            .aspectRatio(16/9, contentMode: .fit)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }

                    if video.progress > 0 {
                        ZStack(alignment: .bottomLeading) {
                            Rectangle()
                                .fill(Color.black.opacity(0.5))
                                .frame(height: 8)
                            Rectangle()
                                .fill(Color.yellow)
                                .frame(height: 8)
                                .scaleEffect(x: CGFloat(video.progress) / 100.0, y: 1.0, anchor: .leading)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                        .aspectRatio(16/9, contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .allowsHitTesting(false)
                    }
                    
                    // Duration label (Liquid Glass)
                    Text(video.duration)
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(.ultraThinMaterial)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                        .padding(10)
                }
                .hoverEffect(.highlight)
                
                // Title Lockup
                VStack(alignment: .leading, spacing: 4) {
                    Text(video.title)
                        .font(.subheadline)
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .multilineTextAlignment(.leading)
                    
                    Text(video.releaseDate)
                        .font(.caption)
                        .foregroundColor(.gray)
                }
                .padding(.horizontal, 4)
                .scaleEffect(isFocused ? 1.1 : 1.0)
                .animation(.easeInOut(duration: 0.2), value: isFocused)
            }
        }
        .buttonStyle(.borderless)
        .focused($isFocused)
    }
}
