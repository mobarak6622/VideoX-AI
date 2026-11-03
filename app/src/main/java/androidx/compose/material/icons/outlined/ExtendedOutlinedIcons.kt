package androidx.compose.material.icons.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Outlined.FolderSpecial: ImageVector
    get() {
        if (_folderSpecial != null) return _folderSpecial!!
        _folderSpecial = materialIcon(name = "Outlined.FolderSpecial") {
            materialPath {
                moveTo(20.0f, 6.0f)
                horizontalLineToRelative(-8.0f)
                lineToRelative(-2.0f, -2.0f)
                lineTo(4.0f, 4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(22.0f, 8.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(20.0f, 18.0f)
                lineTo(4.0f, 18.0f)
                lineTo(4.0f, 6.0f)
                horizontalLineToRelative(5.17f)
                lineToRelative(2.0f, 2.0f)
                lineTo(20.0f, 8.0f)
                verticalLineToRelative(10.0f)
                close()
                moveTo(13.08f, 14.04f)
                lineTo(12.39f, 17.0f)
                lineTo(15.0f, 15.47f)
                lineTo(17.61f, 17.0f)
                lineToRelative(-0.69f, -2.96f)
                lineToRelative(2.3f, -1.99f)
                lineToRelative(-3.03f, -0.26f)
                lineTo(15.0f, 9.0f)
                lineToRelative(-1.19f, 2.79f)
                lineToRelative(-3.03f, 0.26f)
                close()
            }
        }
        return _folderSpecial!!
    }
private var _folderSpecial: ImageVector? = null
