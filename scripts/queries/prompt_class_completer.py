
import inspect
from prompt_toolkit.completion import Completer, Completion

__all__ = (
    'ClassCompleter'
)


def get_methods(methods, prefix, ignore_private):
    for method_name, method in methods:
        if prefix and not method_name.startswith(prefix):
            continue
        if ignore_private and method_name.startswith('_'):
            continue
        if prefix:
            method_name = method_name[len(prefix):]
        yield method_name, method.__doc__


class ClassCompleter(Completer):
    """
    Class-based autocompletion for prompt_toolkit

    :param cls: class to read methods from.
    :param prefix: Optional, prefix indicating methods to include in the
        completion.
    :param ignore_case: If True, case-insensitive completion.
    :param ignore_private: If True, don't include python private-by-convention
        method (those starting with single underscore).
    :param trim_docstr: If True, removes trailing whitespace and reduces docstr
        to the firstline (Completer display_meta renders only one-liners)
    :param include_docstr: If True, include method docstrings as autocomplete
        metadata.
    :param match_middle: When True, match not only the start, but also in the
        middle of the word.
    """
    def __init__(self, cls, prefix=None, ignore_case=False, match_middle=False,
                 trim_docstr=True, ignore_private=True, include_docstr=True):
        self.prefix = prefix
        self.ignore_case = ignore_case
        self.match_middle = match_middle
        self.trim_docstr = trim_docstr
        self.ignore_private = ignore_private
        self.include_docstr = include_docstr
        self.words = {method: docstr
                      for (method, docstr)
                      in get_methods(inspect.getmembers(cls),
                                     self.prefix, self.ignore_private)}

    def get_completions(self, document, complete_event):
        word_before_cursor = document.get_word_before_cursor()

        if self.ignore_case:
            word_before_cursor = word_before_cursor.lower()

        def word_matches(word):
            """ True when the word before the cursor matches. """
            if self.ignore_case:
                word = word.lower()

            if self.match_middle:
                return word_before_cursor in word
            else:
                return word.startswith(word_before_cursor)

        for word in self.words.keys():
            if word_matches(word):
                if self.include_docstr:
                    display_meta = self.words[word]
                    if self.trim_docstr:
                        display_meta = display_meta.strip().split("\n")
                        if len(display_meta) > 0:
                            display_meta = display_meta[0]
                        else:
                            display_meta = ""
                else:
                    display_meta = ""
                yield Completion(word, -len(word_before_cursor),
                                 display_meta=display_meta)
