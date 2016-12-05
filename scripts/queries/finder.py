
import re
import sys
import time
import inspect
from pprint import pprint
from collections import defaultdict

import pymongo
from pymongo import MongoClient

import printers


class ParseError(Exception):
    def __init__(self, value, expected, *args, **kwargs):
        super(ParseError, self).__init__(*args, **kwargs)
        self.value = value
        self.expected = expected


def parse_rest(rest, schema):
    """
    Recursively parse a list of rest arguments according to a schema.
    A schema is a dict from optional argument to a list of corresponding
    positional values:
     {'output': ['filename']}
    Returns a dict from optional arguments to dicts of positional argument
    name to positional argument value.
    """
    def rec(rest, acc):
        if not rest:
            return acc
        arg = rest.pop(0)
        if arg in schema:
            try:
                acc[arg] = {}
                for subarg in schema[arg]:
                    acc[arg][subarg] = rest.pop(0)
                return rec(rest, acc)
            except IndexError:
                value = " ".join([arg] + list(acc[arg].values()))
                expected = " ".join([arg] + list(schema[arg]))
                raise ParseError(value, expected)
        else:
            raise ParseError(arg, "One of " + ", ".join(schema.keys()))
    return rec(rest, {})


class Finder(object):
    def __init__(self, uri, db, verbose=True, timeout=3, **kwargs):
        self.uri = uri
        self.verbose = verbose
        self.timeout = timeout
        try:
            self.conn = MongoClient(self.uri, **kwargs)[db]
            print("Connected to {conn}".format(conn=self.conn))
        except pymongo.errors.ConnectionFailure as e:
            print("Couldn't connect to MongoDB: {e}".format(e=e))
        self.filters = defaultdict(list)
        self.sort_props = {}
        self.config = {}

    def _get_suggestions(self, cmd):
        return [method[len('do_'):]
                for (method, _) in inspect.getmembers(self)
                if cmd in method and method.startswith('do_')]

    def _print_suggestions(self, cmd):
        suggs = self._get_suggestions(cmd)
        if len(suggs) == 0:
            print("I don't understand ¯\(°_o)/")
            return
        print("Do you mean?:")
        for sugg in suggs:
            print("    " + sugg)

    def query_filters(self):
        """Parse currently stored filter values into a pymongo query dict"""
        def filter_value(val):
            if len(val) == 1:
                filter_val = val[0]
                is_regex = re.match(r"/([^/]+)/", filter_val)
                if is_regex:
                    return {"$regex": is_regex.groups()[0]}
                else:
                    return filter_val
            if len(val) > 1:
                return {"$in": val}
        return {f: filter_value(val) for (f, val) in self.filters.items()}

    def parse(self, text):
        cmd, *args = text.split()  # this is python3 only (I think)
        try:
            method = getattr(self, "do_" + cmd)
            return method(args)

        except AttributeError:
            self._print_suggestions(cmd)
            return

        except pymongo.errors.AutoReconnect as e:
            print("Reconnecting... in %d seconds" % self.timeout)
            time.sleep(self.timeout)
            return
        # AutoReconnect is a subclass of ConnectionFailure
        except pymongo.errors.ConnectionFailure as e:
            print("MongoDB Exception {te}: {e}".format(te=type(e), e=e))

    def add_sort(self, prop, order='des'):
        self.sort_props[prop] = order

    def clear_sort_props(self):
        self.sort_props = {}

    def get_filter(self, filter_key):
        return self.filters[filter_key]

    def add_to_filter(self, filter_key, filter_val):
        self.filters[filter_key].append(filter_val)
        if self.verbose:
            pprint(self.query_filters())

    def reset_filter(self, filter_key, filter_val):
        self.filters[filter_key] = [filter_val]
        if self.verbose:
            pprint(self.query_filters())

    def clear_filters(self):
        print("Cleared all filters")
        self.filters = defaultdict(list)

    def clear_filter(self, filter_key):
        if filter_key in self.filters:
            print("Cleared filter {f}".format(f=filter_key))
            del self.filters[filter_key]
        if self.verbose:
            pprint(self.query_filters())

    """
    API functions. First docstr line is used for in-line autocompletion help.
    API-methods must start with do_ to be picked by the CLI-parser.
    Each API-method gets passed a list of 0 or more arguments.
    Argument validation must be done via one of:
       assert custom_assertion, "Informative assertion message",
         It checks for a custom assertion and displays a messsage to the user.
       raise ParseError(given_value, expected_value),
         Custom exception class. This is useful for fine-grained exception
         handling in case of methods that accept arglists (e.g. do_filter).
    API-methods can return None or a (possibly nested) generator of
    (prompt_text, input_callback), such that the prompt_text is shown to the
    user and the input_callback is called on the user's response.
    Note that input_callback can itself return another generator that will be
    called on the user response to the first prompt (thereby allowing for
    recursive workflows - see `do_filter` for an example).
    """

    def do_show(self, args):
        """
        Show the current value of a particular settings (e.g. filters).
        """
        vals = ('filters', 'sort')
        assert len(args) == 1, "Specify at least one value"
        assert args[0] in vals, "Specify one of {vals}".format(vals=vals)
        if args[0] == 'filters':
            for f in self.filters:
                fs = ', '.join(self.filters[f])
                print('    {f} => {fs}'.format(f=f, fs=fs))
        elif args[0] == 'sort':
            for sort_prop, order in self.sort_props.items():
                print('    {s}[{o}]'.format(
                    s=sort_prop,
                    o={'asc': 'ascending', 'des': 'descending'}[order]))

    def do_filter(self, args):
        """
        Add a filter to the query.
        Multiple filters can be added using the following syntax:
          filter key1:value key2:value ...
        Input a key to clear the filter for that key:
          filter username
        Input no arguments to clear all filters:
          filter
        Possible keys are [username, corpus, query]
        """

        ALREADY_EXISTS = \
            "A value for filter {key} already exists, " + \
            "do you want to (o)verwrite or (c)oncatenate?"

        def callback(res):
            if res == 'o':
                self.reset_filter(key, val)
            elif res == 'c':
                self.add_to_filter(key, val)
            else:
                yield "Please answer (o,c)", callback

        if not args:
            self.clear_filters()
            return

        for arg in args:
            if ":" not in arg:  # assume arg is filter_key
                self.clear_filter(arg)
                return
            try:                # assume arg is key:val
                key, val = arg.split(":")
                f = self.get_filter(key)
                if not f:
                    self.add_to_filter(key, val)
                if val in f:
                    continue
                else:
                    yield ALREADY_EXISTS.format(key=key), callback
            except ValueError:
                raise(ParseError(arg, "key:value"))

    def do_sort(self, args):
        """
        Add sort criteria to the query output.
        Multiple sort criteria can be added following the pattern:
          sort field1:asc field2:des
        Specify `asc` or `des` for ascending or descending order.
        Order defaults to descending order.
        Possible value for field are [timestamp, username, corpus].
        """
        orders = ('asc', 'des')
        for arg in args:
            try:
                prop, order = arg.split(':')
                assert order in orders, "Sort order must be in " + str(orders)
                self.add_sort(prop, order=order)
            except ValueError:
                self.add_sort(arg)

    def do_reconnect(self, args):
        """
        Refresh MongoClient connection.
        """
        raise pymongo.errors.AutoReconnect()

    def do_config(self, args):
        """
        Not implemented yet
        Change session configuration.
          Values:
            page_size: type int, default 10
        """
        raise NotImplementedError()

    def do_exit(self, args):
        """
        Exit the application.
        """
        print("See you soon!")
        sys.exit(0)

    def do_help(self, args):
        """
        Show help. Takes a command and display corresponding info.
        Example:
          help filter
        """
        if len(args) == 0:
            print("Please, specify a command.")
            return
        try:
            cmd, *rest = args   # ignore rest arguments
            print("   " + getattr(self, "do_" + cmd).__doc__.strip())
        except AttributeError:
            self._print_suggestions(cmd)

    def get_coll_names(self):
        for coll_name in self.conn.collection_names():
            if coll_name.startswith('_') and coll_name != "_vcs":
                yield coll_name

    def get_project_name(self, coll_name):
        return coll_name[1:]

    def get_project_names(self):
        for coll_name in self.get_coll_names():
            yield self.get_project_name(coll_name)

    def get_project(self, project):
        return self.conn['_' + project]

    def get_projects(self):
        for project_name in self.get_project_names():
            yield self.get_project(project_name)

    def do_projects(self, args):
        """
        Show information about projects
          projects: show existing projects
        """
        if len(args) == 0:
            for project_name in self.get_project_names():
                print(project_name)

    def groupcounts(self, project, groupkeys):
        result = project.aggregate(
            [{"$match": self.query_filters()},
             {"$group": {"_id": {k: "$" + k for k in groupkeys},
                         "count": {"$sum": 1}}}])
        return list(result)

    def do_count(self, args):
        """
        Count annotations using the current filters.
        Specify a project to get only counts for that project:
          count myProject
        Multiple projects can be specified with commas:
          count myProject,projectTest,otherProject
        """
        assert args, "Specify a project (e.g. GET) or 'all' for all projects"

        if self.verbose:
            pprint(self.query_filters())

        # parse arguments
        project, *rest = args
        parsed_args = parse_rest(rest, {'groupby': ['key1,key2,etc'],
                                        'output':  ['filename']})

        # gather projects
        if project == 'all':
            projects = self.get_project_names()
        else:
            projects = project.split(',')

        for project_name in projects:

            # compute counts
            project = self.get_project(project_name)
            if 'groupby' in parsed_args:
                groupkeys = parsed_args['groupby']['key1,key2,etc'].split(',')
                counts = self.groupcounts(project, groupkeys)
            else:
                counts = project.find(self.query_filters()).count()

            # display output
            if 'output' in parsed_args:
                if 'groupby' in parsed_args:
                    raise NotImplementedError()
                else:
                    raise NotImplementedError()
            else:
                if 'groupby' in parsed_args:
                    printers.print_count_group(counts, project_name)
                else:
                    printers.print_count(counts, project_name)
