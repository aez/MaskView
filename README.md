# MaskView
Android View that creates the appearance of holes through other Views.  In this screenshot, a MaskView is stacked on top of an ImageView containing a green square:

![MaskView demo screenshot](http://chalcodes.com/wiki/images/7/73/MaskViewDemo.png)

The MaskView is defined in layout XML with two custom parameters: the resource ID of an alpha mask, and the layout ID of a delegate View.  It uses the delegate View's background Drawable to draw itself, creating the appearance of holes through any other Views beneath it, down to the delegate View's background.
