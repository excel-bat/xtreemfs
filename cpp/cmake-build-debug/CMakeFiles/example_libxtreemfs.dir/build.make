# CMAKE generated file: DO NOT EDIT!
# Generated by "Unix Makefiles" Generator, CMake Version 3.7

# Delete rule output on recipe failure.
.DELETE_ON_ERROR:


#=============================================================================
# Special targets provided by cmake.

# Disable implicit rules so canonical targets will work.
.SUFFIXES:


# Remove some rules from gmake that .SUFFIXES does not remove.
SUFFIXES =

.SUFFIXES: .hpux_make_needs_suffix_list


# Suppress display of executed commands.
$(VERBOSE).SILENT:


# A target that is always out of date.
cmake_force:

.PHONY : cmake_force

#=============================================================================
# Set environment variables for the build.

# The shell in which to execute make rules.
SHELL = /bin/sh

# The CMake executable.
CMAKE_COMMAND = /Applications/CLion.app/Contents/bin/cmake/bin/cmake

# The command to remove a file.
RM = /Applications/CLion.app/Contents/bin/cmake/bin/cmake -E remove -f

# Escaping for special characters.
EQUALS = =

# The top-level source directory on which CMake was run.
CMAKE_SOURCE_DIR = /Users/shanyingbo/IdeaProjects/xdfs/cpp

# The top-level build directory on which CMake was run.
CMAKE_BINARY_DIR = /Users/shanyingbo/IdeaProjects/xdfs/cpp/cmake-build-debug

# Include any dependencies generated for this target.
include CMakeFiles/example_libxtreemfs.dir/depend.make

# Include the progress variables for this target.
include CMakeFiles/example_libxtreemfs.dir/progress.make

# Include the compile flags for this target's objects.
include CMakeFiles/example_libxtreemfs.dir/flags.make

CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.o: CMakeFiles/example_libxtreemfs.dir/flags.make
CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.o: ../src/example_libxtreemfs/example_libxtreemfs.cpp
	@$(CMAKE_COMMAND) -E cmake_echo_color --switch=$(COLOR) --green --progress-dir=/Users/shanyingbo/IdeaProjects/xdfs/cpp/cmake-build-debug/CMakeFiles --progress-num=$(CMAKE_PROGRESS_1) "Building CXX object CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.o"
	/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/c++   $(CXX_DEFINES) $(CXX_INCLUDES) $(CXX_FLAGS) -o CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.o -c /Users/shanyingbo/IdeaProjects/xdfs/cpp/src/example_libxtreemfs/example_libxtreemfs.cpp

CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.i: cmake_force
	@$(CMAKE_COMMAND) -E cmake_echo_color --switch=$(COLOR) --green "Preprocessing CXX source to CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.i"
	/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/c++  $(CXX_DEFINES) $(CXX_INCLUDES) $(CXX_FLAGS) -E /Users/shanyingbo/IdeaProjects/xdfs/cpp/src/example_libxtreemfs/example_libxtreemfs.cpp > CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.i

CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.s: cmake_force
	@$(CMAKE_COMMAND) -E cmake_echo_color --switch=$(COLOR) --green "Compiling CXX source to assembly CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.s"
	/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/c++  $(CXX_DEFINES) $(CXX_INCLUDES) $(CXX_FLAGS) -S /Users/shanyingbo/IdeaProjects/xdfs/cpp/src/example_libxtreemfs/example_libxtreemfs.cpp -o CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.s

CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.o.requires:

.PHONY : CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.o.requires

CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.o.provides: CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.o.requires
	$(MAKE) -f CMakeFiles/example_libxtreemfs.dir/build.make CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.o.provides.build
.PHONY : CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.o.provides

CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.o.provides.build: CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.o


# Object files for target example_libxtreemfs
example_libxtreemfs_OBJECTS = \
"CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.o"

# External object files for target example_libxtreemfs
example_libxtreemfs_EXTERNAL_OBJECTS =

example_libxtreemfs: CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.o
example_libxtreemfs: CMakeFiles/example_libxtreemfs.dir/build.make
example_libxtreemfs: libxtreemfs.a
example_libxtreemfs: ../thirdparty/protobuf-2.6.1/src/.libs/libprotobuf.a
example_libxtreemfs: /usr/local/lib/libboost_system.a
example_libxtreemfs: /usr/local/lib/libboost_thread.a
example_libxtreemfs: /usr/local/lib/libboost_program_options.a
example_libxtreemfs: /usr/local/lib/libboost_regex.a
example_libxtreemfs: /usr/local/lib/libboost_chrono.a
example_libxtreemfs: /usr/local/lib/libboost_date_time.a
example_libxtreemfs: /usr/local/lib/libboost_atomic.a
example_libxtreemfs: /opt/local/lib/libssl.dylib
example_libxtreemfs: /opt/local/lib/libcrypto.dylib
example_libxtreemfs: CMakeFiles/example_libxtreemfs.dir/link.txt
	@$(CMAKE_COMMAND) -E cmake_echo_color --switch=$(COLOR) --green --bold --progress-dir=/Users/shanyingbo/IdeaProjects/xdfs/cpp/cmake-build-debug/CMakeFiles --progress-num=$(CMAKE_PROGRESS_2) "Linking CXX executable example_libxtreemfs"
	$(CMAKE_COMMAND) -E cmake_link_script CMakeFiles/example_libxtreemfs.dir/link.txt --verbose=$(VERBOSE)

# Rule to build all files generated by this target.
CMakeFiles/example_libxtreemfs.dir/build: example_libxtreemfs

.PHONY : CMakeFiles/example_libxtreemfs.dir/build

CMakeFiles/example_libxtreemfs.dir/requires: CMakeFiles/example_libxtreemfs.dir/src/example_libxtreemfs/example_libxtreemfs.cpp.o.requires

.PHONY : CMakeFiles/example_libxtreemfs.dir/requires

CMakeFiles/example_libxtreemfs.dir/clean:
	$(CMAKE_COMMAND) -P CMakeFiles/example_libxtreemfs.dir/cmake_clean.cmake
.PHONY : CMakeFiles/example_libxtreemfs.dir/clean

CMakeFiles/example_libxtreemfs.dir/depend:
	cd /Users/shanyingbo/IdeaProjects/xdfs/cpp/cmake-build-debug && $(CMAKE_COMMAND) -E cmake_depends "Unix Makefiles" /Users/shanyingbo/IdeaProjects/xdfs/cpp /Users/shanyingbo/IdeaProjects/xdfs/cpp /Users/shanyingbo/IdeaProjects/xdfs/cpp/cmake-build-debug /Users/shanyingbo/IdeaProjects/xdfs/cpp/cmake-build-debug /Users/shanyingbo/IdeaProjects/xdfs/cpp/cmake-build-debug/CMakeFiles/example_libxtreemfs.dir/DependInfo.cmake --color=$(COLOR)
.PHONY : CMakeFiles/example_libxtreemfs.dir/depend

