# `class Multiscales`

List of images to represent likely the same content just at different
resolution. There are thus lists of

- `Axis`: each defined as *name* (?? names available only for *spatial*
  types??), *unit*, *type* (one of *channel*, *time*, *spatial*)

- `Dataset`: has its *path* (probably "2nd level zarr folders") and a list of
  `CoordinateTransformations` (must be the scales)

- `CoordinateTransformations`: each broken into *scale* and *translation*, with
  decoration string *type* (??; probably informative) and *path* (??; probably
  leads to some `.json` where the transformation is defined)
