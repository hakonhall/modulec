package no.ion.modulec.file;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.PathType;

import java.nio.file.FileSystem;

public class TestFileSystem {
    /** Returns a new file system with /work as the working directory (and /work exists). */
    public static FileSystem create() {
        // Setting attributes views "unix", and getting the attributes of a file lacks these attributes:
        //    owner (UnixUserPrincipals$User): fixed by adding "owner"
        //    the file type of the mode. The lack of this means it's not possible to get non-standard types,
        //        but these could not be created in JimFS anyways, so this is OK.
        //    permissions set: fixed by adding "owner"
        //    group (UnixUserPrincipals$Group): fixped by adding "owner"
        return Jimfs.newFileSystem(Configuration.builder(PathType.unix())
                                                .setRoots("/")
                                                .setWorkingDirectory("/work")
                                                .setAttributeViews("unix", "owner")
                                                .build());
    }
}
