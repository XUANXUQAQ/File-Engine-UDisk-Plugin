# This is a plugin to make File-Engine support indexing non-NTFS disk.
## How to use
Download the plugin, and put it into the plugins folder.

### NOTICE : Only File-Engine V2.1 and above support plugins.
***
### usage:
1. When you plug the U disk into the computer,    
You will see a tip like this   
----> Type ">udisk> drive letter" to index the U disk   
Just input what the tip says, and you will receive another tip like this   
----> Search Done.

2. **When the search has done. You can input ">udisk test" to search files whose name includeing "test".**   
Example 1 : ***">udisk test"***  --->  files or dirs that including "test"("TEST" "Test" "TEst"...).   
Example 2 : ***">udisk test1;test2"***  --->  files or dirs that including "test1(TEST1)" AND "test2(TEST2)"   
**You can also use some filters like ":f(file)" ":d(directory)" ":full" ":case".Different filters should be separated by semicolons.**   
Example 1 : ***">udisk test:f"***  ---->  Only files that including "test"("TEST" "Test" "TEst"...).   
Example 2 : ***">udisk test:d"***  ---->  Only directories that including "test"("TEST" "Test" "TEst"...).   
Example 3 : ***">udisk test:full"*** --->  files or dirs whose name is "test"("TEST" "Test" "TEst"...).   
Example 4 : ***">udisk test:case"*** ---> files or dirs that including "test".   
Example 5 : ***">udisk test:f;full"***  --->  Only files whose name is "test"("TEST" "Test" "TEst"...).   
Example 6 : ***">udisk test:d;case;full"***  --->  Only dirs whose name is "test".   

![U331ne.png](https://s1.ax1x.com/2020/07/12/U331ne.png)