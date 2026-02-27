import zarr
import ome_zarr
import ome_zarr.writer as ZW

import numpy as np
import math


def prepare_downscaled_3Ddata(ref_img, downscale, diagonal_pattern=False):
    ds = [ int(d) for d in downscale ] # cast the full array to integers
    shape = [ sz//d for sz,d in zip(ref_img.shape, ds) ]
    #
    img,_ = prepare_fullres_3Ddata(shape, diagonal_pattern)
    return img, ds


def prepare_fullres_3Ddata(shape, diagonal_pattern=False):
    if len(shape) != 3:
        print(f"dimensionality of the shape {shape} is not exactly 3")
        return None, None

    step = 50
    bg_value = 20
    line_value = 60

    img = np.empty(shape, dtype='uint8')
    img[...] = bg_value

    if diagonal_pattern:
        #step = float(step) / math.sqrt(2.0)
        for z in range(shape[0]):
            for y in range(shape[1]):
                for x in range(shape[2]):
                    # equation of a diagonal line: y = x
                    # equations of parallel lines: y = x + n*step
                    # equations of parallel lines, shifted by z: y = x + n*step +z
                    # n = (y-x-z) / step
                    real_part_of_n = abs(math.remainder(float(y-x-z), step))
                    if real_part_of_n < 0.5:
                        img[z,y,x] = line_value
                    #
                    real_part_of_n = abs(math.remainder(float(y+x-z), step))
                    if real_part_of_n < 0.5:
                        img[z,y,x] = line_value
    else:
        for z in range(shape[0]):
            draw_z = math.remainder(z,step) == 0
            for y in range(shape[1]):
                draw_zy = draw_z or (math.remainder(y,step) == 0)
                #print(y,draw,(math.remainder(y,step) == 0))
                for x in range(shape[2]):
                    if draw_zy or (math.remainder(x,step) == 0):
                        img[z,y,x] = line_value

    return img, [1,1,1]


def create_pyramid():
    print("preparing base-level image...")
    img0,scale0 = prepare_fullres_3Ddata([500,1000,1000])
    print("preparing 1-level image...")
    img1,scale1 = prepare_downscaled_3Ddata(img0, [2,2,2], True)
    print("preparing 2-level image...")
    img2,scale2 = prepare_downscaled_3Ddata(img0, [2,4,4])
    #img0.shape, img1.shape
    #scale0, scale1
    print("adjusting global intensities per level")
    img1[...] = 100
    img0[...] = 180

    #paths = ["scale0", "scale1", "scale2"]
    #NB: paths are hard-coded to '0', '1', ...

    transformations = [
        [{"type": "scale", "scale": scale0}],
        [{"type": "scale", "scale": scale1}],
        [{"type": "scale", "scale": scale2}]
    ]

    path = "pyramid_v4.zarr"
    print(f"writing to {path}...")
    fmt = ome_zarr.format.FormatV04()
    root = zarr.open_group(path, mode="w", zarr_format = fmt.zarr_format)
    ZW.write_multiscale([img0,img1,img2], root, axes="zyx", fmt=fmt, \
            coordinate_transformations=transformations, \
            storage_options=dict(chunks=(125, 250, 250)))

    path = "pyramid_v5.zarr"
    print(f"writing to {path}...")
    fmt = ome_zarr.format.FormatV05()
    root = zarr.open_group(path, mode="w", zarr_format = fmt.zarr_format)
    ZW.write_multiscale([img0,img1,img2], root, axes="zyx", fmt=fmt, \
            coordinate_transformations=transformations, \
            storage_options=dict(chunks=(125, 250, 250)))


if __name__=="__main__":
    create_pyramid()
