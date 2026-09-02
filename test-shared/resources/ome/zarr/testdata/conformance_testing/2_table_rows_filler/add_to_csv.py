import pandas as pd
import verify_ome_zarr as V

def convert_from_table(old_table):
    new_table = pd.DataFrame([])
    for _, row in old_table.iterrows():
        print("Inspecting path:", row['File Path'], end="")
        try:
            new_content = V._create_expected(row['File Path'],'')
            new_content['StudyName'] = row['Study']
            new_content['License'] = row['License']
            new_table = pd.concat([new_table, pd.DataFrame([new_content])], ignore_index=True)
            print("  done")
        except Exception as e:
            print("  Error! ",e)
    return new_table


def convert_from_v0_table():
    old_table = pd.read_csv('../testbed_datasets.csv')
    table = old_table[  (old_table['OME-NGFF version'] >= 0.4)
                      & (old_table['OME-NGFF version'] <= 0.5)                        # version restriction
                      & (old_table['File Path'].str.contains("livingobjects.ebi"))    # source site restriction
                      & (old_table['Wells'].isna()) ]                                 # non-HCS sources
    return table


def reproduce_table_v1():
    import pandas as pd
    import add_to_csv

    t = add_to_csv.convert_from_v0_table()
    t2a = add_to_csv.convert_from_table(t)

    # bf2raw "failers" -- have to be used with explicit 'PathToImageMultiscales':
    q = V._create_expected('https://livingobjects.ebi.ac.uk/idr/zarr/v0.4/idr0048A/9846151.zarr','/0')
    q['StudyName']='idr0048A'
    q['License']='CC BY 4.0'
    t2a = pd.concat([t2a, pd.DataFrame([q])])

    q = V._create_expected('https://livingobjects.ebi.ac.uk/idr/zarr/v0.5/idr0026/3.66.9-6.141020_15-41-29.00.ome.zarr','/0')
    q['StudyName']='idr0026'
    q['License']='CC BY 4.0'
    t2a = pd.concat([t2a, pd.DataFrame([q])])

    q = V._create_expected('https://livingobjects.ebi.ac.uk/idr/zarr/v0.5/idr0051/180712_H2B_22ss_Courtney1_20180712-163837_p00_c00_preview.zarr','/0')
    q['StudyName']='idr0051'
    q['License']='CC BY 4.0'
    t2a = pd.concat([t2a, pd.DataFrame([q])])

    for i in range(9):
        q = V._create_expected('https://livingobjects.ebi.ac.uk/idr/zarr/v0.5/idr0033A/BR00109990_C2.zarr',f"/{i}")
        q['StudyName']=f"idr0033A_{i}"
        q['License']='CC BY 4.0'
        t2a = pd.concat([t2a, pd.DataFrame([q])])

    for i in range(3):
        q = V._create_expected('https://livingobjects.ebi.ac.uk/idr/zarr/v0.4/idr0079A/idr0079_images.zarr',f"/{i}")
        q['StudyName']=f"idr0079A_{i}"
        q['License']='CC BY 4.0'
        t2a = pd.concat([t2a, pd.DataFrame([q])])

