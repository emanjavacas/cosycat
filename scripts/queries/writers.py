
import csv


def header_from_results(results):
    return list(results[0].keys())


# String-formatters for stdout output


def _print_count(counts, project_name):
    count_text = "Found [{counts}] annotations in project [{project}]"
    print(count_text.format(counts=counts, project=project_name))


def _print_count_group(counts, project_name):
    print(project_name)
    if len(counts) == 0:  # output might be empty
        print()
        return
    print("---")
    header = header_from_results(counts)
    maxlen = max([len(k) for k in header]) + 5
    padstr = '%-' + str(maxlen) + 's'
    print(padstr * len(header) % tuple(header))
    for line in counts:
        vals = [line[k] for k in header]
        print(padstr * len(header) % tuple(vals))
    print()


def print_count(by_project):
    for project_name, counts in by_project.items():
        _print_count(counts, project_name)


def print_count_group(by_project):
    for project_name, counts in by_project.items():
        _print_count_group(counts, project_name)


# CSV writers


def csv_count(by_project, outfile):
    results = [{'count': c, 'project': p} for p, c in by_project.items()]
    header = header_from_results(results)
    with open(outfile, 'w') as f:
        csvwriter = csv.DictWriter(f, delimiter=',', fieldnames=header)
        # write header
        csvwriter.writerow({field: field for field in header})
        # write rows
        for row in results:
            csvwriter.writerow(row)


def csv_count_group(by_project, outfile):
    results = [dict(row, project=p)
               for p, counts in by_project.items()
               for row in counts]
    header = header_from_results(results)
    with open(outfile, 'w') as f:
        csvwriter = csv.DictWriter(f, delimiter=',', fieldnames=header)
        # write header
        csvwriter.writerow({field: field for field in header})
        # write rows
        for row in results:
            csvwriter.writerow({field: row[field] for field in header})
