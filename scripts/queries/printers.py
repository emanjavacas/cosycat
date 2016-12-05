
def print_count(result, project):
    count_text = "Found [{result}] annotations in project [{project}]"
    print(count_text.format(result=result, project=project))


def print_count_group(result, project):
    print(project)
    if len(result) == 0:  # output might be empty
        print()
        return
    print("---")
    header = list(sorted(result[0]['_id'].keys()))
    maxlen = max([len(k) for k in header]) + 5
    padstr = '%-' + str(maxlen) + 's'
    print(padstr * len(header) % tuple(header))
    for line in result:
        vals = [line['_id'][k] for k in header]
        print(padstr * len(header) % tuple(vals) + padstr % line['count'])
    print()
