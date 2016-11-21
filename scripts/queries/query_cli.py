
import os

from prompt_class_completer import ClassCompleter
from finder import Finder, ParseError
from prompt_toolkit import prompt
from prompt_toolkit.history import InMemoryHistory, FileHistory


def get_default_histfile():
    return os.path.join(os.path.expanduser('~'), '.cosycatcli_history')

if __name__ == '__main__':
    import argparse
    import getpass
    parser = argparse.ArgumentParser(description='Cosycat Query CLI')
    parser.add_argument('host')
    parser.add_argument('-H', '--history_file', default=get_default_histfile())
    parser.add_argument('-i', '--input_prompt', default='> ')
    parser.add_argument('-p', '--port', type=int, default=27017)
    parser.add_argument('-u', '--user')
    parser.add_argument('-P', '--password')
    parser.add_argument('-v', '--verbose', default=False, action='store_true')

    args = parser.parse_args()
    if args.user and not args.password:
        pw = getpass.getpass(prompt='Enter password for [%s]\n' % args.user)
    elif args.user and args.password:
        pw = args.password

    if args.user:
        uri = 'mongodb://{user}:{pw}@{host}:{port}/cosycat'. \
              format(user=args.user, pw=pw, host=args.host, port=args.port)
    else:
        uri = 'mongodb://{host}:{port}/cosycat'. \
              format(host=args.host, port=args.port)

    finder = Finder(uri, verbose=args.verbose)
    try:
        history = FileHistory(args.history_file)
    except Exception:
        print("Couldn't read input history. Using in-memory backend instead.")
        history = InMemoryHistory()
    completer = ClassCompleter(Finder, prefix='do_')

    def recur(res):
        if res:
            for question, callback in res:
                recur(callback(prompt(question + '\n')))

    while True:
        try:
            text = prompt(
                args.input_prompt, history=history, completer=completer)
            if not text.split():            # skip whitespace input
                continue
            recur(finder.parse(text))
        except KeyboardInterrupt:
            finder.do_exit(None)
        except NotImplementedError:
            print("Functionality is not yet implemented... :-(")
        except ParseError as e:
            print("Bad input \'{value}', expected format is \'{expected}\'".
                  format(value=e.value, expected=e.expected))
        except AssertionError as e:
            print("Bad input value: {assertion}".format(assertion=e))
