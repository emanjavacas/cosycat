
import csv


def get_header_from_group(grouped_result):
    return list(sorted(grouped_result[0]['_id'].keys()))

# String-formatters for stdout output


def print_count(result, project):
    count_text = "Found [{result}] annotations in project [{project}]"
    print(count_text.format(result=result, project=project))


def print_count_group(result, project):
    print(project)
    if len(result) == 0:  # output might be empty
        print()
        return
    print("---")
    header = get_header_from_group(result)
    maxlen = max([len(k) for k in header]) + 5
    padstr = '%-' + str(maxlen) + 's'
    print(padstr * len(header) % tuple(header))
    for line in result:
        vals = [line['_id'][k] for k in header]
        print(padstr * len(header) % tuple(vals) + padstr % line['count'])
    print()


# CSV writers


def csv_count(result, project, outfile):
    header = ['count', 'project']
    with open(outfile, 'wb') as f:
        csvwriter = csv.DictWriter(f, delimiter=',', fieldnames=header)
        # write header
        csvwriter.writerow({fieldname: fieldname for fieldname in header})
        for row in [{'count': result, 'project': project}]:
            csvwriter.writerow(row)


def csv_count_group(result, project, outfile):
    if len(result) == 0:
        print("Omitting empty results for project [%s]" % project)
        return
    header = get_header_from_group(result)
    with open(outfile, 'wb') as f:
        csvwriter = csv.DictWriter(f, delimiter=',', fieldnames=header)
        # write header
        csvwriter.writerow({fieldname: fieldname for fieldname in header})
        for row in result:
            csvwriter.writerow({row['_id'][fieldname] for fieldname in header})
