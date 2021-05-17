mkdir acpcommander 2>/dev/null

find ../src -name "*.java" > sources.txt

javac -d . @sources.txt
if [ $? -ne 0 ]; then
   echo "Compile failed, quitting"
   exit 99
fi

find . -name "*.class" > classes.txt

jar -cvfe acp_commander.jar acpcommander.launch.AcpCommander @classes.txt
if [ $? -ne 0 ]; then
   echo "CLI Jar build failed, quitting"
   exit 98
fi

jar -cvfe acp_commander_gui.jar acpcommander.launch.AcpCommanderGui @classes.txt
if [ $? -ne 0 ]; then
   echo "GUI Jar build failed, quitting"
   exit 97
fi

rm -r acpcommander/
rm sources.txt
rm classes.txt