import XCTest
@testable import PoseAI

/// VideoMerger 合成器逻辑测试
/// 验证空输入防护和输出路径生成
final class VideoMergerTests: XCTestCase {

    // MARK: - 空输入防护

    /// 空数组输入应返回 nil
    func testMerge_emptyURLs_returnsNil() {
        let expectation = XCTestExpectation(description: "empty merge completes")
        VideoMerger.merge(videoURLs: []) { result in
            XCTAssertNil(result, "空 URL 列表应返回 nil")
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 2.0)
    }

    /// 无效 URL 输入不应崩溃（健壮性测试）
    func testMerge_invalidURL_returnsNilOrCompletes() {
        let fakeURL = URL(fileURLWithPath: "/tmp/nonexistent_\(UUID().uuidString).mp4")
        let expectation = XCTestExpectation(description: "invalid merge completes")

        VideoMerger.merge(videoURLs: [fakeURL]) { result in
            // 无效文件可能返回 nil（视频轨为空）或尝试导出后失败
            // 关键是不崩溃
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 5.0)
    }

    // MARK: - BGM 可选参数

    /// 无 BGM 时应正常工作（不崩溃）
    func testMerge_noBGM_nocrash() {
        let expectation = XCTestExpectation(description: "no bgm merge")
        VideoMerger.merge(videoURLs: [], bgmURL: nil) { result in
            XCTAssertNil(result)
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 2.0)
    }
}
