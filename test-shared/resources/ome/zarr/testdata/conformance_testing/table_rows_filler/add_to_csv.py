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


