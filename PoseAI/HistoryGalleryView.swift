import SwiftUI
import SwiftData

// MARK: - 拍摄历史浏览页 (从 SwiftData 读取)
struct HistoryGalleryView: View {
    @Environment(\.dismiss) var dismiss

    // 按创建时间倒序查询所有拍摄记录
    @Query(sort: \ShootingRecord.createdAt, order: .reverse)
    private var records: [ShootingRecord]

    // 状态管理
    @State private var selectedRecord: ShootingRecord? = nil
    @State private var loadedImage: UIImage? = nil
    @State private var isLoadingImage = false

    // V2: 月份筛选状态（nil = 全部）
    @State private var selectedMonth: String? = nil

    // 网格布局（3 列）
    let columns = [
        GridItem(.flexible(), spacing: 2),
        GridItem(.flexible(), spacing: 2),
        GridItem(.flexible(), spacing: 2)
    ]

    // MARK: - V2 计算属性：可用月份列表
    private var availableMonths: [String] {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM"
        let months = Set(records.map { formatter.string(from: $0.createdAt) })
        return months.sorted(by: >)
    }

    // MARK: - 计算属性：按天分组（支持月份过滤）
    var groupedRecords: [(String, [ShootingRecord])] {
        let filtered: [ShootingRecord]
        if let month = selectedMonth {
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyy-MM"
            filtered = records.filter { formatter.string(from: $0.createdAt) == month }
        } else {
            filtered = Array(records)
        }
        let grouped = Dictionary(grouping: filtered) { $0.dateString }
        // 对键（日期）倒序排序
        return grouped.sorted { $0.key > $1.key }
    }

    var body: some View {
        NavigationView {
            ZStack {
                Color.black.ignoresSafeArea()

                if records.isEmpty {
                    emptyStateView
                } else {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 0) {
                            // V2: 月份筛选器
                            if availableMonths.count > 1 {
                                monthSelector
                                    .padding(.bottom, 12)
                            }

                            LazyVStack(alignment: .leading, spacing: 24) {
                                if groupedRecords.isEmpty {
                                    // 当前月无记录
                                    VStack(spacing: 12) {
                                        Image(systemName: "calendar.badge.exclamationmark")
                                            .font(.system(size: 36, weight: .ultraLight))
                                            .foregroundColor(Design.textSecondary)
                                        Text("该月暂无拍摄记录")
                                            .font(.system(size: 14))
                                            .foregroundColor(Design.textSecondary)
                                    }
                                    .frame(maxWidth: .infinity)
                                    .padding(.top, 60)
                                }

                                ForEach(groupedRecords, id: \.0) { dateStr, dailyRecords in
                                    VStack(alignment: .leading, spacing: 8) {
                                        // 日期 Header
                                        Text(dateStr)
                                            .font(.system(size: 18, weight: .bold))
                                            .foregroundColor(.white)
                                            .padding(.horizontal, 16)

                                        // 图片网格
                                        LazyVGrid(columns: columns, spacing: 2) {
                                            ForEach(dailyRecords) { record in
                                                HistoryThumbnailCell(record: record)
                                                    .aspectRatio(1, contentMode: .fill)
                                                    .onTapGesture {
                                                        openDetail(record)
                                                    }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        .padding(.vertical, 16)
                    }
                }
            }
            .navigationTitle("历史图库")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    NavigationLink(destination: StatsView()) {
                        Image(systemName: "chart.bar.xaxis")
                            .foregroundColor(.white)
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("完成") { dismiss() }
                        .foregroundColor(Design.accent)
                }
            }
            .sheet(item: Binding(
                get: { selectedRecord },
                set: { if $0 == nil { selectedRecord = nil; loadedImage = nil } }
            )) { record in
                detailSheet
            }
        }
        .preferredColorScheme(.dark)
    }

    // MARK: - V2 月份筛选器 UI
    private var monthSelector: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                // "全部"按钮
                monthCapsule(label: "全部", isActive: selectedMonth == nil) {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        selectedMonth = nil
                    }
                }

                ForEach(availableMonths, id: \.self) { month in
                    monthCapsule(label: formatMonthLabel(month), isActive: selectedMonth == month) {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            selectedMonth = month
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
        }
    }

    private func monthCapsule(label: String, isActive: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 13, weight: isActive ? .bold : .medium))
                .foregroundColor(isActive ? .black : .white.opacity(0.7))
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(
                    Capsule()
                        .fill(isActive ? Design.accent : Color.white.opacity(0.1))
                )
                .overlay(
                    Capsule()
                        .stroke(isActive ? Design.accent.opacity(0.6) : Color.white.opacity(0.15), lineWidth: 1)
                )
        }
    }

    /// 将 "2026-04" 格式化为 "2026年4月"
    private func formatMonthLabel(_ yearMonth: String) -> String {
        let parts = yearMonth.split(separator: "-")
        guard parts.count == 2,
              let year = Int(parts[0]),
              let month = Int(parts[1]) else { return yearMonth }
        return "\(year)年\(month)月"
    }

    // MARK: - 空状态
    private var emptyStateView: some View {
        VStack(spacing: 16) {
            Image(systemName: "photo.on.rectangle")
                .font(.system(size: 48, weight: .ultraLight))
                .foregroundColor(Design.textSecondary)
            Text("暂无拍摄记录")
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(.white)
            Text("去拍几张照片，它们会出现在这里")
                .font(.system(size: 14))
                .foregroundColor(Design.textSecondary)
        }
    }

    // MARK: - 大图弹窗
    private var detailSheet: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if let img = loadedImage {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFit()
                    .ignoresSafeArea()
            } else if isLoadingImage {
                ProgressView()
                    .tint(.white)
            } else {
                VStack(spacing: 12) {
                    Image(systemName: "exclamationmark.triangle")
                        .font(.largeTitle)
                        .foregroundColor(Design.danger)
                    Text("原图已在相册中被删除或无法访问")
                        .font(.callout)
                        .foregroundColor(.white)
                }
            }

            // 顶部信息栏
            VStack {
                HStack(alignment: .top) {
                    // 关闭按钮
                    Button {
                        selectedRecord = nil
                        loadedImage = nil
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 28))
                            .foregroundColor(.white.opacity(0.8))
                    }

                    Spacer()

                    if let r = selectedRecord {
                        // 悬浮信息卡片
                        VStack(alignment: .trailing, spacing: 6) {
                            Text(r.sceneType.displayName)
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(Design.accent)
                            Text(r.planName)
                                .font(.system(size: 12))
                                .foregroundColor(.white)
                            HStack(spacing: 4) {
                                Image(systemName: "star.fill")
                                Text("\(r.matchScore)")
                            }
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(r.matchScore >= 80 ? Design.success : .white)

                            if let filter = r.appliedFilterRawValue, let config = PhotoFilter(rawValue: filter) {
                                Text("\(config.icon) \(config.displayName)")
                                    .font(.system(size: 10))
                                    .foregroundColor(Design.textSecondary)
                                    .padding(.top, 4)
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Design.overlayBg, in: RoundedRectangle(cornerRadius: 12))
                    }
                }
                .padding()
                Spacer()
            }
        }
    }

    // MARK: - 动作
    private func openDetail(_ record: ShootingRecord) {
        selectedRecord = record
        isLoadingImage = true
        loadedImage = nil

        Task {
            let img = await PhotoAlbumUtil.shared.fetchImage(by: record.localIdentifier)
            await MainActor.run {
                self.loadedImage = img
                self.isLoadingImage = false
            }
        }
    }
}

// MARK: - 网格图 Cell
struct HistoryThumbnailCell: View {
    let record: ShootingRecord

    @State private var image: UIImage? = nil
    @State private var didFail = false

    var body: some View {
        ZStack {
            Color.white.opacity(0.1)

            if let img = image {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFill()
            } else if didFail {
                Image(systemName: "photo.badge.exclamationmark")
                    .foregroundColor(.white.opacity(0.3))
            } else {
                ProgressView()
                    .scaleEffect(0.8)
            }

            // 底部黑边加分数字段
            VStack {
                Spacer()
                HStack {
                    Spacer()
                    Text("\(record.matchScore)")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(record.matchScore >= 80 ? Design.success : .white)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 2)
                        .background(Color.black.opacity(0.6))
                        .cornerRadius(4)
                }
                .padding(4)
            }
        }
        .clipped()
        .task {
            // 异步加载原图（相册会自动提供缩略图）
            if let img = await PhotoAlbumUtil.shared.fetchImage(by: record.localIdentifier) {
                await MainActor.run { self.image = img }
            } else {
                await MainActor.run { self.didFail = true }
            }
        }
    }
}
