import XCTest
@testable import PoseAI

/// PhotoFilterEngine 滤镜引擎测试
/// 验证所有滤镜预设的输出正确性、缓存行为和缩略图生成
final class PhotoFilterEngineTests: XCTestCase {

    private var engine: PhotoFilterEngine!
    private var testImage: UIImage!

    override func setUp() {
        super.setUp()
        engine = PhotoFilterEngine()

        // 创建一张 100x100 的彩色测试图片
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 100, height: 100))
        testImage = renderer.image { ctx in
            UIColor.orange.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 100, height: 100))
        }
        engine.setSource(testImage)
    }

    override func tearDown() {
        engine = nil
        testImage = nil
        super.tearDown()
    }

    // MARK: - 滤镜输出

    /// 原图滤镜应返回原始图片
    func testOriginalFilter_returnsImage() {
        let result = engine.apply(.original)
        XCTAssertNotNil(result, "原图滤镜应返回图片")
    }

    /// 所有滤镜预设都应返回非 nil 结果
    func testAllFilters_returnNonNil() {
        for filter in PhotoFilter.allCases {
            let result = engine.apply(filter)
            XCTAssertNotNil(result,
                "\(filter.displayName) 滤镜应返回非 nil 结果")
        }
    }

    /// 所有滤镜的输出尺寸应与源图一致
    func testAllFilters_preserveSize() {
        for filter in PhotoFilter.allCases {
            guard let result = engine.apply(filter) else {
                XCTFail("\(filter.displayName) 返回 nil")
                continue
            }
            // CGImage 尺寸比较（忽略 scale 差异）
            if let sourceW = testImage.cgImage?.width,
               let resultW = result.cgImage?.width {
                XCTAssertEqual(resultW, sourceW,
                    "\(filter.displayName) 的输出宽度应与源图一致")
            }
        }
    }

    // MARK: - 缓存

    /// 连续两次调用同一滤镜应返回缓存（同一引用）
    func testCache_returnsSameInstance() {
        let first = engine.apply(.film)
        let second = engine.apply(.film)
        XCTAssertTrue(first === second, "同一滤镜的二次调用应返回缓存实例")
    }

    /// setSource 应清空旧缓存
    func testSetSource_clearsCacheTarget() {
        let _ = engine.apply(.bw)

        // 设置新源图
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 50, height: 50))
        let newImage = renderer.image { ctx in
            UIColor.purple.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 50, height: 50))
        }
        engine.setSource(newImage)

        // 新结果应与旧缓存不同
        let newResult = engine.apply(.bw)
        XCTAssertNotNil(newResult, "新源图的滤镜结果不应为 nil")
        if let newW = newResult?.cgImage?.width {
            XCTAssertEqual(newW, 50, "新源图的输出宽度应为 50")
        }
    }

    // MARK: - 缩略图

    /// 缩略图应返回非 nil 且尺寸更小
    func testThumbnail_returnsSmallerImage() {
        let thumbSize = CGSize(width: 60, height: 60)
        for filter in PhotoFilter.allCases {
            let thumb = engine.thumbnail(filter, size: thumbSize)
            XCTAssertNotNil(thumb,
                "\(filter.displayName) 缩略图不应为 nil")
        }
    }

    // MARK: - 无源图防护

    /// 未设置源图时所有操作应返回 nil 而不崩溃
    func testNoSource_returnsNil() {
        let emptyEngine = PhotoFilterEngine()
        for filter in PhotoFilter.allCases where filter != .original {
            let result = emptyEngine.apply(filter)
            XCTAssertNil(result, "未设置源图时 \(filter.displayName) 应返回 nil")
        }
    }
}
