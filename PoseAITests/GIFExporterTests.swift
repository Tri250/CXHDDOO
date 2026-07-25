import XCTest
@testable import PoseAI

/// GIFExporter 导出器测试
/// 验证 GIF 导出的边界条件和文件生成
final class GIFExporterTests: XCTestCase {

    // MARK: - 空输入

    /// 空图片数组应返回 nil
    func testExport_emptyImages_returnsNil() {
        let expectation = XCTestExpectation(description: "empty export")
        GIFExporter.export(images: []) { url in
            XCTAssertNil(url, "空图片数组应返回 nil")
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 3.0)
    }

    // MARK: - 正常导出

    /// 传入有效图片应成功导出 GIF 文件
    func testExport_validImages_producesFile() {
        let images = (0..<3).map { _ -> UIImage in
            let renderer = UIGraphicsImageRenderer(size: CGSize(width: 50, height: 50))
            return renderer.image { ctx in
                UIColor.blue.setFill()
                ctx.fill(CGRect(x: 0, y: 0, width: 50, height: 50))
            }
        }

        let expectation = XCTestExpectation(description: "gif export")
        GIFExporter.export(images: images, filename: "test_export.gif") { url in
            XCTAssertNotNil(url, "有效图片应成功导出 GIF")
            if let url = url {
                XCTAssertTrue(FileManager.default.fileExists(atPath: url.path),
                    "导出的 GIF 文件应存在")
                // 清理
                try? FileManager.default.removeItem(at: url)
            }
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 5.0)
    }

    // MARK: - 自定义延迟

    /// 自定义帧延迟不应崩溃
    func testExport_customDelays_nocrash() {
        let images = (0..<2).map { _ -> UIImage in
            let renderer = UIGraphicsImageRenderer(size: CGSize(width: 10, height: 10))
            return renderer.image { ctx in
                UIColor.green.setFill()
                ctx.fill(CGRect(x: 0, y: 0, width: 10, height: 10))
            }
        }

        let expectation = XCTestExpectation(description: "custom delay export")
        GIFExporter.export(images: images, delays: [0.5, 0.8], filename: "test_delay.gif") { url in
            XCTAssertNotNil(url, "自定义延迟应正常导出")
            if let url = url {
                try? FileManager.default.removeItem(at: url)
            }
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 5.0)
    }
}
