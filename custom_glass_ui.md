Custom Page Scenes

In the CXR-M SDK, a customizable display interface is provided. Developers can display custom content on the glasses without developing glasses-side pages by using custom interface scenes.

# 1 Open Custom Interface

You can open a custom view through the `fun openCustomView(content: String):ValueUtil.CxrStatus?` interface. The content parameter contains the page initialization content.

```kotlin
/**
 * open custom view
 *
 * @param content json format view content
 *
 * @return open request status
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun openCustomView(content: String): ValueUtil.CxrStatus {
    return CxrApi.getInstance().openCustomView(content)
```

# 2 Monitor Custom Page Status

You can monitor custom view status by setting the `CustomViewListener`.

```kotlin
// Custom View Listener
private val customViewListener = object : CustomViewListener {
    /**
     * custom view icons sent
     */
    override fun onIconsSent() {
    }

    /**
     * custom view opened
     */
    override fun onOpened() {
    }

    /**
     * custom view closed
     */
    override fun onOpenFailed(p0: Int) {
    }

    /**
     * custom view updated
     */
    override fun onUpdated() {
    }

    /**
     * custom view closed
     */
    override fun onClosed() {
    }
}

/**
 * set custom view listener (true: set listener, false: remove listener)
 */
fun setCustomViewListener(set: Boolean){
    CxrApi.getInstance().setCustomViewListener(if (set) customViewListener else null)
}
```

# 3 Update Page

You can update page display through the `fun updateCustomView(content: String): ValueUtil.CxrStatus?` interface.

```kotlin
/**
 * update custom view
 * @param content: custom view content that need update
 * @return: update request status
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun updateCustomView(content: String): ValueUtil.CxrStatus {
    return CxrApi.getInstance().updateCustomView(content)
}
```

# 4 Close Page

You can close the custom page scene through the `fun closeCustomView(): ValueUtil.CxrStatus?` interface.

```kotlin
/**
 * close custom view
 * @return close request status
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun closeCustomView(): ValueUtil.CxrStatus {
    return CxrApi.getInstance().closeCustomView()
}
```

# 5 Upload Image Resources

If page resources contain images, they need to be uploaded in advance. There are resolution requirements for images - they should not exceed 128*128px. To ensure response speed, the number of images should also be controlled, preferably no more than 10 images.

```kotlin
/**
 * Send custom icons to glasses
 *
 * @param icons icons to send
 * @see IconInfo
 * @return send custom icons request status
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request success
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun sendCustomIcons(icons: List<IconInfo>): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().sendCustomViewIcons(icons)
}
```

Where the `IconInfo` class contains two parameters: `name`: used to identify the photo during initialization or update; `data`: Base64 encoded image data. Note that only the green channel of the image will be displayed on the glasses, other channels will not be displayed.

# 6 Initialization JSON Format

Note that page descriptions use JSON.

Supported layouts: `LinearLayout` and `RelativeLayout`.

| Widget                                                                                                                                                                             | Parameter                                                                       | Value |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- | ----- |
| LinearLayout                                                                                                                                                                       | id                                                                              | [id]  |
| layout_width <br>layout_height                                                                                                                                                     | match_parent <br>wrap_content <br>[value]dp                                     |       |
| layout_gravity(controls child widget gravity) <br>gravity                                                                                                                          | start<br>top<br>end<br>bottom<br>center<br>center_horizontal<br>center_vertical |       |
| orientation                                                                                                                                                                        | vertical<br>horizontal                                                          |       |
| marginStart<br>marginTop<br>marginEnd<br>marginBottom                                                                                                                              | [value]dp                                                                       |       |
| layout_weight(controls child widget weight)                                                                                                                                        | [value]                                                                         |       |
| paddingStart<br>paddingTop<br>paddingEnd<br>paddingBottom                                                                                                                          | [value]dp                                                                       |       |
| backgroundColor                                                                                                                                                                    | #FF000000                                                                       |       |
| RelativeLayout                                                                                                                                                                     | id                                                                              | [id]  |
| layout_width<br>layout_height                                                                                                                                                      | match_parent<br>wrap_content<br>[value]dp                                       |       |
| paddingStart<br>paddingEnd<br>paddingTop<br>paddingBottom                                                                                                                          | [value]dp                                                                       |       |
| backgroundColor                                                                                                                                                                    | #FF000000                                                                       |       |
| marginStart<br>marginEnd<br>marginTop<br>marginBottom                                                                                                                              | [value]dp                                                                       |       |
| layout_toStartOf<br>layout_above<br>layout_toEndOf<br>layout_below<br>layout_alignBaseLine<br>layout_alignStart<br>layout_alignEnd<br>layout_alignTop<br>layout_alignBottom        | [id]                                                                            |       |
| layout_alignParentStart<br>layout_alignParentEnd<br>layout_alignParentTop<br>layout_alignParentBottom<br>layout_centerInParent<br>layout_centerHorizontal<br>layout_centerVertical | true<br>false                                                                   |       |

Supported widgets: `TextView`, `ImageView`

| Widget                                                    | Parameter                                                                                        | Value |
| --------------------------------------------------------- | ------------------------------------------------------------------------------------------------ | ----- |
| TextView                                                  | id                                                                                               | [id]  |
| layout_width<br/>layout_height                            | match_parent<br>wrap_content<br>[value]dp                                                        |       |
| text                                                      | [text]                                                                                           |       |
| textColor                                                 | #FF00FF00                                                                                        |       |
| textSize                                                  | [value]sp                                                                                        |       |
| gravity                                                   | start<br>top<br>end<br>bottom<br>center<br>center_horizontal<br>center_vertical                  |       |
| textStyle                                                 | bold<br>italic                                                                                   |       |
| paddingStart<br>paddingEnd<br>paddingTop<br>paddingBottom | [value]dp                                                                                        |       |
| marginStart<br>marginEnd<br>marginTop<br>marginBottom     | [value]dp                                                                                        |       |
| ImageView                                                 | id                                                                                               | [id]  |
| layout_width<br>layout_height                             | match_parent<br>wrap_content<br>[value]dp                                                        |       |
| name                                                      | [icon_name]                                                                                      |       |
| scaleType                                                 | matrix<br>fix_xy<br>fix_start<br>fix_center<br>fix_end<br>center<br>center_crop<br>center_inside |       |

Here is a simple initialization JSON example:

```json
{
  "type": "LinearLayout",
  "props": {
    "layout_width": "match_parent",
    "layout_height": "match_parent",
    "orientation": "vertical",
    "gravity": "center_horizontal",
    "paddingTop": "140dp",
    "paddingBottom": "100dp",
    "backgroundColor": "#FF000000"
  },
  "children": [
    {
      "type": "TextView",
      "props": {
        "id": "tv_title",
        "layout_width": "wrap_content",
        "layout_height": "wrap_content",
        "text": "Init Text",
        "textSize": "16sp",
        "textColor": "#FF00FF00",
        "textStyle": "bold",
        "marginBottom": "20dp"
      }
    },
    {
      "type": "RelativeLayout",
      "props": {
        "width": "match_parent",
        "height": "100dp",
        "backgroundColor": "#00000000",
        "padding": "10dp"
      },
      "children": [
        {
          "type": "ImageView",
          "props": {
            "id": "iv_icon",
            "layout_width": "60dp",
            "layout_height": "60dp",
            "name": "icon_name0",
            "layout_alignParentStart": "true",
            "layout_centerVertical": "true"
          }
        },
        {
          "type": "TextView",
          "props": {
            "id": "tv_text",
            "layout_width": "wrap_content",
            "layout_height": "wrap_content",
            "text": "Text to the end of Icon",
            "textSize": "16sp",
            "textColor": "#FF00FF00",
            "layout_toEndOf": "iv_icon",
            "layout_centerVertical": "true",
            "marginStart": "15dp"
          }
        }
      ]
    }
  ]
}
```

# 7 Update JSON Format

When passing update content, JSON format is also used. The content must include the id to be updated. For images, pass the corresponding image id.

Here is a simple example:

```Json
[
  {
    "action": "update",
    "id": "tv_title",
    "props": {
      "text": "Update Text"
    }
  },
  {
    "action": "update",
    "id": "iv_icon",
    "props": {
      "name": "icon_name1"
    }
  }
]
```
