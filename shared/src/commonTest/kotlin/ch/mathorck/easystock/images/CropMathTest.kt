package ch.mathorck.easystock.images

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CropMathTest {
    private val w = 1000f
    private val h = 600f
    private val full = CropRect(0f, 0f, w, h)

    private fun assertRect(expected: CropRect, actual: CropRect) {
        val e = 0.01f
        assertEquals(expected.left, actual.left, e, "left")
        assertEquals(expected.top, actual.top, e, "top")
        assertEquals(expected.right, actual.right, e, "right")
        assertEquals(expected.bottom, actual.bottom, e, "bottom")
    }

    @Test
    fun initialCropIsTheWholeImageOrTheLargestSquare() {
        assertRect(full, initialCrop(w, h, CropAspect.FREE))
        assertRect(CropRect(200f, 0f, 800f, 600f), initialCrop(w, h, CropAspect.SQUARE))
    }

    @Test
    fun minSizeNeverExceedsTheImage() {
        assertEquals(MIN_CROP_PX, minCropSize(1000f, 600f))
        assertEquals(20f, minCropSize(20f, 600f))
    }

    @Test
    fun moveIsClampedInsideTheImage() {
        val r = CropRect(100f, 100f, 300f, 200f)
        assertRect(CropRect(0f, 0f, 200f, 100f), dragCrop(r, CropHandle.MOVE, -500f, -500f, w, h))
        assertRect(CropRect(800f, 500f, 1000f, 600f), dragCrop(r, CropHandle.MOVE, 5000f, 5000f, w, h))
        assertRect(CropRect(150f, 120f, 350f, 220f), dragCrop(r, CropHandle.MOVE, 50f, 20f, w, h))
    }

    @Test
    fun freeEdgeAndCornerDrags() {
        val r = CropRect(100f, 100f, 300f, 200f)
        assertRect(CropRect(100f, 100f, 400f, 200f), dragCrop(r, CropHandle.RIGHT, 100f, 77f, w, h))
        assertRect(CropRect(60f, 100f, 300f, 200f), dragCrop(r, CropHandle.LEFT, -40f, 0f, w, h))
        assertRect(CropRect(100f, 100f, 350f, 260f), dragCrop(r, CropHandle.BOTTOM_RIGHT, 50f, 60f, w, h))
        assertRect(CropRect(0f, 0f, 300f, 200f), dragCrop(r, CropHandle.TOP_LEFT, -900f, -900f, w, h))
    }

    @Test
    fun freeDragCannotCollapseBelowTheMinimum() {
        val r = CropRect(100f, 100f, 300f, 200f)
        val shrunk = dragCrop(r, CropHandle.RIGHT, -5000f, 0f, w, h)
        assertEquals(MIN_CROP_PX, shrunk.width, 0.01f)
        assertEquals(100f, shrunk.left, 0.01f)
        val corner = dragCrop(r, CropHandle.TOP_LEFT, 5000f, 5000f, w, h)
        assertEquals(MIN_CROP_PX, corner.width, 0.01f)
        assertEquals(MIN_CROP_PX, corner.height, 0.01f)
        assertEquals(300f, corner.right, 0.01f)
        assertEquals(200f, corner.bottom, 0.01f)
    }

    @Test
    fun squareCornerKeepsTheAnchorAndTheRatio() {
        val r = CropRect(100f, 100f, 300f, 300f)
        assertRect(CropRect(100f, 100f, 400f, 400f), dragCrop(r, CropHandle.BOTTOM_RIGHT, 100f, 100f, w, h, ratio = 1f))
        assertRect(CropRect(50f, 50f, 300f, 300f), dragCrop(r, CropHandle.TOP_LEFT, -50f, -50f, w, h, ratio = 1f))
    }

    @Test
    fun squareCornerIsLimitedByTheImageBounds() {
        val r = CropRect(100f, 100f, 300f, 300f)
        // 500 px of height below the anchor, 900 of width: the square stops at 500
        val out = dragCrop(r, CropHandle.BOTTOM_RIGHT, 5000f, 5000f, w, h, ratio = 1f)
        assertRect(CropRect(100f, 100f, 600f, 600f), out)
    }

    @Test
    fun squareEdgeKeepsTheCenterOfTheOtherAxis() {
        val r = CropRect(100f, 100f, 300f, 300f)
        assertRect(CropRect(100f, 50f, 400f, 350f), dragCrop(r, CropHandle.RIGHT, 100f, 0f, w, h, ratio = 1f))
        assertRect(CropRect(50f, 0f, 350f, 300f), dragCrop(r, CropHandle.TOP, 0f, -100f, w, h, ratio = 1f))
    }

    @Test
    fun lockedEdgeStaysInsideTheImage() {
        val r = CropRect(100f, 10f, 200f, 110f)
        val out = dragCrop(r, CropHandle.RIGHT, 5000f, 0f, w, h, ratio = 1f)
        assertTrue(out.top >= 0f && out.bottom <= h && out.right <= w)
        assertEquals(out.width, out.height, 0.01f)
        assertEquals(100f, out.left, 0.01f)
    }

    @Test
    fun lockedDragNeverGoesBelowTheMinimum() {
        val r = CropRect(100f, 100f, 300f, 300f)
        val out = dragCrop(r, CropHandle.BOTTOM_RIGHT, -5000f, -5000f, w, h, ratio = 1f)
        assertEquals(MIN_CROP_PX, out.width, 0.01f)
        assertEquals(MIN_CROP_PX, out.height, 0.01f)
    }

    @Test
    fun switchingToSquareKeepsTheCenter() {
        assertRect(CropRect(200f, 100f, 400f, 300f), withAspect(CropRect(100f, 100f, 500f, 300f), 1f, w, h))
        val same = CropRect(1f, 2f, 3f, 4f)
        assertEquals(same, withAspect(same, null, w, h))
    }

    @Test
    fun hitTestPicksCornersEdgesAndInside() {
        val r = CropRect(100f, 100f, 500f, 400f)
        assertEquals(CropHandle.TOP_LEFT, hitTest(r, 105f, 95f, 20f))
        assertEquals(CropHandle.BOTTOM_RIGHT, hitTest(r, 490f, 410f, 20f))
        assertEquals(CropHandle.TOP, hitTest(r, 300f, 110f, 20f))
        assertEquals(CropHandle.LEFT, hitTest(r, 90f, 250f, 20f))
        assertEquals(CropHandle.RIGHT, hitTest(r, 505f, 250f, 20f))
        assertEquals(CropHandle.BOTTOM, hitTest(r, 300f, 395f, 20f))
        assertEquals(CropHandle.MOVE, hitTest(r, 300f, 250f, 20f))
        assertNull(hitTest(r, 800f, 800f, 20f))
    }

    @Test
    fun hitTestOnATinyRectStillOffersMove() {
        val r = CropRect(100f, 100f, 148f, 148f)
        assertEquals(CropHandle.MOVE, hitTest(r, 124f, 124f, 40f))
    }

    @Test
    fun rotatingTheRectFollowsTheImage() {
        // 1000x600 image turned clockwise becomes 600x1000: the top-left area goes to the top-right
        assertRect(CropRect(500f, 0f, 600f, 200f), rotateRect90(CropRect(0f, 0f, 200f, 100f), 600f))
        var cur = CropRect(100f, 50f, 400f, 300f)
        var curW = 1000f
        var curH = 600f
        repeat(4) {
            cur = rotateRect90(cur, curH)
            val t = curW
            curW = curH
            curH = t
        }
        assertRect(CropRect(100f, 50f, 400f, 300f), cur)
    }

    @Test
    fun viewportMapsBothWays() {
        val vp = fitViewport(1000f, 500f, 400f, 400f, margin = 20f)
        assertEquals(0.36f, vp.scale, 0.0001f)
        assertEquals(20f, vp.offsetX, 0.01f)
        assertEquals(110f, vp.offsetY, 0.01f)
        assertEquals(500f, vp.toImageX(vp.toViewX(500f)), 0.01f)
        assertEquals(250f, vp.toImageY(vp.toViewY(250f)), 0.01f)
    }

    @Test
    fun boxCoversTheRectAndStaysInside() {
        assertEquals(CropBox(10, 20, 31, 41), toCropBox(CropRect(10.4f, 20.9f, 30.2f, 40.1f), 100, 100))
        assertEquals(CropBox(0, 0, 100, 100), toCropBox(CropRect(-5f, -5f, 500f, 500f), 100, 100))
        val tiny = toCropBox(CropRect(99.9f, 99.9f, 99.95f, 99.95f), 100, 100)
        assertTrue(tiny.width >= 1 && tiny.height >= 1 && tiny.right <= 100)
    }

    @Test
    fun orientationSizeAndInverse() {
        assertEquals(600 to 1000, orientedSize(6, 1000, 600))
        assertEquals(1000 to 600, orientedSize(3, 1000, 600))
        for (o in 1..8) {
            val (ow, oh) = orientedSize(o, 1000, 600)
            val box = CropBox(10, 20, 110, 220)
            val there = mapBox(o, box, 1000, 600)
            assertEquals(box, mapBox(inverseOrientation(o), there, ow, oh), "orientation $o")
        }
    }

    @Test
    fun sourceBoxOfAnUprightFileIsTheBox() {
        val box = CropBox(10, 20, 110, 220)
        assertEquals(box, sourceBox(box, 1000, 600, 1, 0))
    }

    @Test
    fun sourceBoxFollowsExifRotation() {
        // Stored 1000x600, orientation 6 (turn 90 cw to show it): displayed 600x1000.
        // The top-left 100x200 area of the display is the bottom-left area of the file.
        assertEquals(CropBox(0, 500, 200, 600), sourceBox(CropBox(0, 0, 100, 200), 1000, 600, 6, 0))
    }

    @Test
    fun sourceBoxFollowsUserRotation() {
        // Upright 1000x600, turned once clockwise: displayed 600x1000. Display top-left = file bottom-left.
        assertEquals(CropBox(0, 500, 200, 600), sourceBox(CropBox(0, 0, 100, 200), 1000, 600, 1, 1))
        // Half turn: display top-left = file bottom-right
        assertEquals(CropBox(900, 500, 1000, 600), sourceBox(CropBox(0, 0, 100, 100), 1000, 600, 1, 2))
    }

    @Test
    fun sourceBoxKeepsTheAreaForEveryCombination() {
        val box = CropBox(30, 40, 130, 190)
        for (exif in 1..8) for (q in 0..3) {
            val src = sourceBox(box, 1000, 600, exif, q)
            assertEquals(box.width * box.height, src.width * src.height, "exif $exif turns $q")
            assertTrue(src.left >= 0 && src.top >= 0 && src.right <= 1000 && src.bottom <= 600, "exif $exif turns $q")
        }
    }

    @Test
    fun turnsOrientation() {
        assertEquals(1, quarterTurnsOrientation(0))
        assertEquals(6, quarterTurnsOrientation(1))
        assertEquals(3, quarterTurnsOrientation(2))
        assertEquals(8, quarterTurnsOrientation(3))
        assertEquals(8, quarterTurnsOrientation(-1))
        assertEquals(6, quarterTurnsOrientation(5))
    }
}
