import SwiftUI
import SwiftData

struct SaveCustomPlanView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    
    // 从外界传入的有效捕捉点云
    let points: [String: CGPoint]
    let onSaveFinished: () -> Void
    
    @State private var poseName: String = ""
    @State private var poseEmoji: String = "✨"
    
    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("姿势预览")) {
                    ZStack {
                        Color.black.opacity(0.8)
                        PoseCanvasPreview(points: points)
                            .padding(20)
                    }
                    .frame(height: 250)
                    .listRowInsets(EdgeInsets()) // 移除列表边距使预览顶满
                }
                
                Section(header: Text("自定义属性"), footer: Text("选择一个直观的 Emoji，并给你的绝美姿势起个好听的名字。它会默认居中构图。")) {
                    HStack {
                        Text("名称")
                            .foregroundColor(Design.textSecondary)
                            .frame(width: 60, alignment: .leading)
                        TextField("例如：显高交叉腿", text: $poseName)
                    }
                    
                    HStack {
                        Text("Emoji")
                            .foregroundColor(Design.textSecondary)
                            .frame(width: 60, alignment: .leading)
                        TextField("🧍", text: $poseEmoji)
                            .onChange(of: poseEmoji) { newValue in
                                // 限制为一个甚至只提取第一个字符
                                if newValue.count > 1 {
                                    poseEmoji = String(newValue.prefix(1))
                                }
                            }
                    }
                }
            }
            .navigationTitle("保存姿势")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("取消") { dismiss() }
                        .foregroundColor(.gray)
                }
                
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("保存") {
                        savePlan()
                    }
                    .foregroundColor(poseName.isEmpty ? .gray : Design.accent)
                    .disabled(poseName.isEmpty)
                }
            }
        }
    }
    
    private func savePlan() {
        let finalEmoji = poseEmoji.isEmpty ? "🧍" : poseEmoji
        let newPlan = CustomPlan(
            poseName: poseName,
            poseEmoji: finalEmoji,
            points: points
        )
        
        modelContext.insert(newPlan)
        
        // 可选提供震动反馈
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.success)
        
        onSaveFinished()
        dismiss()
    }
}

// 供保存视图使用的小型线框骨架预览
struct PoseCanvasPreview: View {
    let points: [String: CGPoint]
    
    let connections: [(String, String)] = [
        ("neck", "leftShoulder"), ("neck", "rightShoulder"),
        ("leftShoulder", "leftElbow"), ("rightShoulder", "rightElbow"),
        ("leftElbow", "leftWrist"), ("rightElbow", "rightWrist"),
        ("neck", "leftHip"), ("neck", "rightHip"),
        ("leftHip", "leftKnee"), ("rightHip", "rightKnee"),
        ("leftKnee", "leftAnkle"), ("rightKnee", "rightAnkle")
    ]
    
    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            
            Canvas { ctx, _ in
                // 先画连接线
                for (j1, j2) in connections {
                    if let p1 = points[j1], let p2 = points[j2] {
                        var path = Path()
                        path.move(to: CGPoint(x: p1.x * w, y: p1.y * h))
                        path.addLine(to: CGPoint(x: p2.x * w, y: p2.y * h))
                        
                        ctx.stroke(
                            path,
                            with: .color(Design.accent),
                            style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round)
                        )
                    }
                }
                
                // 再画节点圆点
                for (_, p) in points {
                    let rect = CGRect(x: p.x * w - 4, y: p.y * h - 4, width: 8, height: 8)
                    ctx.fill(Path(ellipseIn: rect), with: .color(.white))
                }
            }
        }
    }
}
