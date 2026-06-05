from pathlib import Path
import pandas as pd


def parse_OpenSciVis_processed_README(path: str) -> list[dict]:
    """
    The processed README shall feature repeating 10-lines-long blocks, e.g.:

    <block start, not part of the file>
    Name: backpack
    Type: uint16
    Size: [512, 512, 373]
    Spacing: [0.9766, 0.9766, 1.25]
    Scales: 3
    HTTPS URL: https://ome-zarr-scivis.s3.us-east-1.amazonaws.com/v0.5/96x2/backpack.ome.zarr
    S3 URL: s3://ome-zarr-scivis/v0.5/96x2/backpack.ome.zarr
    OZX HTTPS URL: https://ome-zarr-scivis.s3.us-east-1.amazonaws.com/v0.5/96x2-ozx/backpack.ozx
    OZX S3 URL: s3://ome-zarr-scivis/v0.5/96x2-ozx/backpack.ozx

    </block end, not part of the file; NOTE THE MANDATORY EMPTY LINE>

    The README (as of 2026/06) found on InsightSoftwareConsortium consisted of 51 records/blocks.
    """
    lines = Path(path).read_text().splitlines()

    assert len(lines) == 51 * 10, f"Expected 510 lines, got {len(lines)}"

    records = []
    for i in range(0, len(lines), 10):
        chunk = lines[i:i + 10]
        record = {}
        for line in chunk:
            key, _, value = line.partition(":")
            record[key.strip()] = value.strip()
        records.append(record)

    return records


def to_dataframe(records: list[dict]) -> "pd.DataFrame":
    rows = []
    for r in records:
        size_x, size_y, size_z = (int(v) for v in r["Size"].strip("[]").split(","))
        rows.append({
            "OME-NGFF version": 0.5,
            "File Path":        r["HTTPS URL"],
            "SizeX":            size_x,
            "SizeY":            size_y,
            "SizeZ":            size_z,
            "Axes":             "XYZ",
            "License":          "Apache-2.0",
            "Study":            r["Name"],
        })

    return pd.DataFrame(rows)


def reduce_columns_in_IDR_table(table):
    """
    Original IDR columns (as of 2026/06) are:

    OME-NGFF version, File Path, SizeX, SizeY, SizeZ, SizeC, SizeT,
    Axes, Wells, Fields, Keywords, License, Study, DOI, Date added,
    Representative Image ID, Thumbnail

    that gets here reduced to (output of this function):

    OME-NGFF version, File Path, SizeX, SizeY, SizeZ, SizeC, SizeT,
    Axes, Wells, Fields, License, Study

    removing these:
    Keywords, DOI, Date added, Representative Image ID, Thumbnail
    """
    return table[[ "OME-NGFF version", "File Path",
                   "SizeX", "SizeY", "SizeZ", "SizeC", "SizeT",
                   "Axes", "Wells", "Fields", "License", "Study" ]]



def this_is_how_to_use():
    t1 = reduce_columns_in_IDR_table( pd.read_csv('IDR_table.csv') )

    list_of_dict = parse_OpenSciVis_processed_README('OMEZarrOpenSciVisDatasets.txt')
    t2 = to_dataframe(list_of_dict)

    df = pd.concat([t1, t2], ignore_index=True)
    df.to_csv('testbed_datasets.csv', index=False)

