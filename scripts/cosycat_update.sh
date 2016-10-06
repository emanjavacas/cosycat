#!/bin/bash

TARGET_DIR=$1
SYMLINK=$2
SOURCE_DIR=$(pwd)

HELP="bash /path/to/cosycat/scripts/cosycat_update.sh deploy-to-dir [symlink-to-jar]"

if [ ! -d "$TARGET_DIR" ]; then
    echo "Couldn't find target dir $TARGET_DIR";
    exit 1;
fi

case `basename $SOURCE_DIR` in
    scripts)
	ROOT_DIR=`dirname $SOURCE_DIR`
	;;
    cosycat)
	ROOT_DIR=$SOURCE_DIR
	;;
    *)
	echo "Couldn't find project root";
	exit 1;
	;;
esac

cd $ROOT_DIR;

echo "Fetching last release";
echo "------------";
git pull
printf "Done...\n";

echo "Cleaning up:";
echo "------------";
lein clean || { echo "Couldn't run 'lein', is leiningen installed?"; exit 1; }
printf "Done...\n"

echo "Compiling JAR file";
echo "------------";
lein uberjar || { echo "Couldn't compile, is leiningen installed?"; exit 1; }
printf "Done...\n"

echo "Moving JAR to $TARGET_DIR"
echo "------------"
if ! ls target/*standalone.jar 1> /dev/null 2>&1; then
    echo "Couldn't find jar executable. Exiting...";
    exit 1;
else
    JAR=`ls target/*standalone.jar`;
fi

cp $JAR $TARGET_DIR/

if [ -n "$SYMLINK" ]; then
    echo "Symlinking to $SYMLINK";
    echo "-----------"
    ln -fs $TARGET_DIR/`basename $JAR` $SYMLINK;
    printf "Done...\n";
fi

echo "Bye!"
cd $SOURCE_DIR;
exit 0;
