#!/bin/bash

# runs all JUnit tests in the subpackage 'org.xtreemfs.test' that end with '*Test.java'

TEST_DIR=$4
if [ -z $TEST_DIR ]
then
  TEST_DIR=/tmp/xtreemfs-junit
  if [ ! -d "$TEST_DIR" ]; then mkdir "$TEST_DIR"; fi
  if [ ! -d "${TEST_DIR}/log" ]; then mkdir "${TEST_DIR}/log"; fi
fi
echo "TEST_DIR: $TEST_DIR"

JUNIT_LOG_FILE="${TEST_DIR}/log/junit.log"

XTREEMFS=$1
if [ -z "$XTREEMFS" ]
then
  # Try to guess the XtreemFS svn root.
  if [ -d "java/servers" ]; then XTREEMFS="."; fi
fi
echo "XTREEMFS=$XTREEMFS"

# find all jars; build the classpath for running the JUnit tests
CLASSPATH="."
while read LINE; do
    CLASSPATH="$CLASSPATH:$LINE"
done < <(find $XTREEMFS/java -name \*.jar)

# find all source files for the unit tests
SOURCES=""
for PROJECT in servers flease foundation
do
  SOURCES="$SOURCES "$(find "java/${PROJECT}/test" -name \*.java -printf "%p ")
done

CLASSES_DIR=$TEST_DIR/classes

# compile JUnit tests; store results in $CLASSES_DIR
mkdir -p $CLASSES_DIR
JAVAC_CALL="$JAVA_HOME/bin/javac -cp $CLASSPATH -d $TEST_DIR/classes $SOURCES"
echo "Compiling tests..."
# echo "Compiling tests: ${JAVAC_CALL}"
$JAVAC_CALL
RESULT=$?
if [ "$RESULT" -ne "0" ]; then echo "$COMMAND failed"; exit $RESULT; fi

CLASSPATH="$CLASSPATH:$CLASSES_DIR"

# find and execute all JUnit tests among the class files
rm -f "$JUNIT_LOG_FILE"
COUNTER=0
FAILED=0
JUNIT_TESTS=""
while read LINE; do

  if [[ $LINE = *ExternalIntegrationTest.class ]]
  then
      # not a valid JUnit test
      continue;
  fi

  # Transform a path of the form "/tmp/xtreemfs-junit/classes/org/xtreemfs/test/mrc/OSDPolicyTest.class" to the form "org.xtreemfs.test.mrc.OSDPolicyTest".
  TEST=`echo $LINE | sed -r -e 's|^.*(org\/xtreemfs\/.+)\.class\$|\1|'`
  # replace '/' with '.'
  TEST=${TEST//\//\.}

  # run each JUnit test separately in its own JVM
  JAVA_CALL="$JAVA_HOME/bin/java -ea -cp $CLASSPATH org.junit.runner.JUnitCore $TEST"

  echo -n "Running test `expr $COUNTER + 1`: $TEST ... "
  $JAVA_CALL >> "$JUNIT_LOG_FILE" 2>&1
  RESULT=$?
  if [ "$RESULT" -ne "0" ]; then
    echo "FAILURE"
    FAILED=`expr $FAILED + 1`
  else
    echo "ok"
  fi
    
  COUNTER=`expr $COUNTER + 1`
    
done < <(find $CLASSES_DIR -name *Test.class -type f)

echo "`expr $COUNTER - $FAILED` / $COUNTER tests successfully executed."

if [ "$FAILED" -ne "0" ]; then exit 1; fi

# Report crashes.
grep "has crashed" "$JUNIT_LOG_FILE" >/dev/null
if [ $? -eq 0 ]
then
  echo "However, during the test services did crash. Examine the log file junit.log for more information."
  exit 2
fi