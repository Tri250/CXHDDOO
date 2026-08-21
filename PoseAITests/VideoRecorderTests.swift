import XCTest
@testable import PoseAI

/// VideoRecorder 录制器核心逻辑测试
/// 验证状态机、片段管理和生命周期
final class VideoRecorderTests: XCTestCase {

    private var recorder: VideoRecorder!

    override func setUp() {
        super.setUp()
        recorder = VideoRecorder(width: 360, height: 640)
    }

    override func tearDown() {
        recorder.reset()
        recorder = nil
        super.tearDown()
    }

    // MARK: - 初始状态

    /// 初始化后不应处于录制状态
    func testInitialState_notRecording() {
        XCTAssertFalse(recorder.isRecording, "初始化后不应处于录制状态")
        XCTAssertTrue(recorder.recordedChunks.isEmpty, "初始化后不应有片段")
    }

    // MARK: - 录制状态机

    /// 开始录制后状态应切换为 true
    func testStartRecording_setsIsRecording() {
        recorder.startRecordingChunk()

        // isRecording 通过 main async 设置，给时间传播
        let expectation = XCTestExpectation(description: "isRecording should be true")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            XCTAssertTrue(self.recorder.isRecording, "开始录制后应处于录制状态")
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 1.0)
    }

    /// 重复调用 startRecordingChunk 不应创建多个 writer
    func testDoubleStart_isIdempotent() {
        recorder.startRecordingChunk()
        recorder.startRecordingChunk() // 二次调用应被忽略

        let expectation = XCTestExpectation(description: "still recording")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            // 没有崩溃即为通过
            XCTAssertTrue(self.recorder.isRecording)
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 1.0)
    }

    /// 停止录制后 isRecording 应为 false
    func testStopRecording_setsNotRecording() {
        recorder.startRecordingChunk()

        let expectation = XCTestExpectation(description: "stopped recording")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            self.recorder.stopRecordingChunk { _ in
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    XCTAssertFalse(self.recorder.isRecording, "停止录制后状态应为 false")
                    expectation.fulfill()
                }
            }
        }
        wait(for: [expectation], timeout: 2.0)
    }

    /// 未录制时调用 stop 应安全返回 nil
    func testStopWithoutStart_returnsNil() {
        let expectation = XCTestExpectation(description: "should return nil")
        recorder.stopRecordingChunk { url in
            XCTAssertNil(url, "未录制时停止应返回 nil")
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 1.0)
    }

    // MARK: - Reset

    /// reset 应清空所有状态
    func testReset_clearsEverything() {
        recorder.startRecordingChunk()

        let expectation = XCTestExpectation(description: "reset complete")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            self.recorder.reset()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                XCTAssertFalse(self.recorder.isRecording, "reset 后不应在录制")
                XCTAssertTrue(self.recorder.recordedChunks.isEmpty, "reset 后切片列表应为空")
                expectation.fulfill()
            }
        }
        wait(for: [expectation], timeout: 2.0)
    }

    // MARK: - 临时目录

    /// tempDir 应自动创建
    func testTempDir_exists() {
        let dir = recorder.tempDir
        var isDir: ObjCBool = false
        let exists = FileManager.default.fileExists(atPath: dir.path, isDirectory: &isDir)
        XCTAssertTrue(exists && isDir.boolValue, "临时目录应自动创建")
    }
}
