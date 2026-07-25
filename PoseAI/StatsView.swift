import SwiftUI
import SwiftData
import Charts

// MARK: - 拍摄数据面板 (Step 6)
struct StatsView: View {
    // 从数据库中获取所有拍摄记录
    @Query(sort: \ShootingRecord.createdAt, order: .forward)
    private var records: [ShootingRecord]

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if records.isEmpty {
                emptyState
            } else {
                ScrollView {
                    VStack(spacing: 24) {
                        // 1. 核心指标卡片
                        SummaryPillars(records: records)

                        // 2. 柱状图：最常用场景统计
                        SceneFrequencyChart(records: records)
                            .padding(.top, 10)

                        // 3. 折线/散点图：历史打分趋势
                        ScoreTrendChart(records: records)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 24)
                }
            }
        }
        .navigationTitle("拍摄数据")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "chart.bar.xaxis")
                .font(.system(size: 48, weight: .ultraLight))
                .foregroundColor(Design.textSecondary)
            Text("暂无数据支撑")
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(.white)
            Text("去多拍几张照片，再来看看你的专属摄影报告吧")
                .font(.system(size: 14))
                .foregroundColor(Design.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
    }
}

// MARK: - 顶部核心数据
struct SummaryPillars: View {
    let records: [ShootingRecord]

    var totalCount: Int {
        records.count
    }

    var avgScore: Int {
        guard !records.isEmpty else { return 0 }
        let total = records.reduce(0) { $0 + $1.matchScore }
        return total / records.count
    }

    var favoriteScene: SceneType {
        let dict = Dictionary(grouping: records, by: { $0.sceneType })
        let sorted = dict.sorted { $0.value.count > $1.value.count }
        return sorted.first?.key ?? .unknown
    }

    var body: some View {
        HStack(spacing: 12) {
            statCard(title: "总拍摄", value: "\(totalCount)", unit: "张", icon: "camera.fill", color: Design.accent)
            statCard(title: "平均得分", value: "\(avgScore)", unit: "分", icon: "star.fill", color: Design.success)
            statCard(title: "最爱场景", value: favoriteScene.displayName, unit: "", icon: favoriteScene.icon, color: .purple)
        }
    }

    private func statCard(title: String, value: String, unit: String, icon: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: icon)
                    .foregroundColor(color)
                    .font(.system(size: 14))
                Text(title)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Design.textSecondary)
            }

            HStack(alignment: .lastTextBaseline, spacing: 2) {
                Text(value)
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .foregroundColor(Design.textPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                if !unit.isEmpty {
                    Text(unit)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Design.textSecondary)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white.opacity(0.08))
        .cornerRadius(16)
    }
}

// MARK: - 场景频率柱状图
struct SceneFrequencyChart: View {
    let records: [ShootingRecord]

    struct SceneStat: Identifiable {
        let id = UUID()
        let scene: String
        let count: Int
    }

    var stats: [SceneStat] {
        let grouped = Dictionary(grouping: records, by: { $0.sceneType })
        return grouped.map { SceneStat(scene: $0.key.displayName, count: $0.value.count) }
            .sorted { $0.count > $1.count } // 按频次倒序
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("场景偏好分布")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.white)
            
            Text("记录了你在不同环境下触发的打卡频次")
                .font(.system(size: 12))
                .foregroundColor(Design.textSecondary)

            Chart(stats) { stat in
                BarMark(
                    x: .value("场景", stat.scene),
                    y: .value("次数", stat.count)
                )
                .foregroundStyle(Design.accent.gradient)
                .cornerRadius(4)
            }
            .chartYAxis {
                AxisMarks(position: .leading) { value in
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [4, 4]))
                        .foregroundStyle(Color.white.opacity(0.1))
                    AxisValueLabel()
                        .foregroundStyle(Design.textSecondary)
                }
            }
            .chartXAxis {
                AxisMarks { value in
                    AxisValueLabel()
                        .foregroundStyle(.white.opacity(0.8))
                }
            }
            .frame(height: 200)
            .padding(.top, 16)
        }
        .padding(20)
        .background(Color.white.opacity(0.05))
        .cornerRadius(20)
    }
}

// MARK: - 得分趋势图
struct ScoreTrendChart: View {
    let records: [ShootingRecord]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("姿势评分趋势")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.white)
            
            Text("展现你每次拍摄的表现，看看有没有进步")
                .font(.system(size: 12))
                .foregroundColor(Design.textSecondary)

            Chart(records) { record in
                LineMark(
                    x: .value("时间", record.createdAt),
                    y: .value("得分", record.matchScore)
                )
                .interpolationMethod(.monotone)
                .foregroundStyle(Design.success)
                .lineStyle(StrokeStyle(lineWidth: 3))

                PointMark(
                    x: .value("时间", record.createdAt),
                    y: .value("得分", record.matchScore)
                )
                .foregroundStyle(Design.success)
                .symbolSize(40)
            }
            .chartYScale(domain: 0...100)
            .chartYAxis {
                AxisMarks(position: .leading, values: [0, 20, 40, 60, 80, 100]) { value in
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [4, 4]))
                        .foregroundStyle(Color.white.opacity(0.1))
                    AxisValueLabel()
                        .foregroundStyle(Design.textSecondary)
                }
            }
            .chartXAxis {
                // 简化 X 轴（可能太挤）
                AxisMarks(values: .automatic(desiredCount: 4)) { value in
                    AxisGridLine()
                        .foregroundStyle(Color.white.opacity(0.05))
                    AxisValueLabel(format: .dateTime.month().day())
                        .foregroundStyle(Design.textSecondary)
                }
            }
            .frame(height: 200)
            .padding(.top, 16)
        }
        .padding(20)
        .background(Color.white.opacity(0.05))
        .cornerRadius(20)
    }
}
