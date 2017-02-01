// This file was generated by Mendix Modeler.
//
// WARNING: Only the following code will be retained when actions are regenerated:
// - the import list
// - the code between BEGIN USER CODE and END USER CODE
// - the code between BEGIN EXTRA CODE and END EXTRA CODE
// Other code you write will be lost the next time you deploy the project.
// Special characters, e.g., é, ö, à, etc. are supported in comments.

package filesimportmodule.actions;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import com.mendix.core.Core;
import com.mendix.logging.ILogNode;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.systemwideinterfaces.core.IMendixObject;
import com.mendix.webui.CustomJavaAction;
import filesimportmodule.proxies.ArchiveType;
import system.proxies.FileDocument;

public class ExtractFile extends CustomJavaAction<java.util.List<IMendixObject>>
{
	private IMendixObject __archiveFile;
	private system.proxies.FileDocument archiveFile;
	private filesimportmodule.proxies.ArchiveType archiveType;

	public ExtractFile(IContext context, IMendixObject archiveFile, java.lang.String archiveType)
	{
		super(context);
		this.__archiveFile = archiveFile;
		this.archiveType = archiveType == null ? null : filesimportmodule.proxies.ArchiveType.valueOf(archiveType);
	}

	@Override
	public java.util.List<IMendixObject> executeAction() throws Exception
	{
		this.archiveFile = __archiveFile == null ? null : system.proxies.FileDocument.initialize(getContext(), __archiveFile);

		// BEGIN USER CODE
		logNode.debug("Start ExtractFile");
		if (this.archiveFile == null) {
			logNode.info("Given archive file is null. Returning null");
			return null;
		}
		java.util.List<IMendixObject> fileList = new ArrayList<>();
		File tmpArchiveFile = null;

		logNode.debug("Archive type set to " + this.archiveType.toString());
		switch (this.archiveType) {
		case ZIP:
			// For ZIP it's advised to use ZipFile
			tmpArchiveFile = createTempFileForFileDoc(this.archiveFile);
			ZipFile zipFile = new ZipFile(tmpArchiveFile);
			try {
				Enumeration<ZipArchiveEntry> archiveEntries = zipFile.getEntries();
				while (archiveEntries.hasMoreElements()) {
					ZipArchiveEntry archiveEntry = archiveEntries.nextElement();
					if (!zipFile.canReadEntryData(archiveEntry)) {
						logNode.warn("Can't read data of an archive entry. Skipping.");
						continue;
					}
					String entryName = archiveEntry.getName();
					if (archiveEntry.isDirectory() || archiveEntry.isUnixSymlink()) {
						logNode.info("Archive entry " + entryName + " is not a file. Skipping.");
						continue;
					}
					InputStream entryInStr = zipFile.getInputStream(archiveEntry);
					FileDocument fileDoc = createFileDocument(entryInStr, archiveEntry);
					fileList.add(fileDoc.getMendixObject());
				}
			} finally {
				ZipFile.closeQuietly(zipFile);
			}
			break;
		case SEVEN_Z:
			tmpArchiveFile = createTempFileForFileDoc(this.archiveFile);
			fileList = getFileDocumentsFrom7zip(tmpArchiveFile);
			break;
		case tar_gz:
			BufferedInputStream gzBfIn = new BufferedInputStream(
					Core.getFileDocumentContent(getContext(), this.__archiveFile));
			CompressorInputStream gzIn = new GzipCompressorInputStream(gzBfIn);
			tmpArchiveFile = createTempFileForInputStream(gzIn, this.archiveFile.getName());
			// Fall-through to tar
		case TAR:
			InputStream tarBfIn;
			if (tmpArchiveFile != null) {
				tarBfIn = new FileInputStream(tmpArchiveFile);
			} else {
				tarBfIn = Core.getFileDocumentContent(getContext(), this.__archiveFile);
			}
			fileList = getFileDocumentsFromArchive(tarBfIn, ArchiveStreamFactory.TAR);
			break;
		default:
			String archiverName;
			if (this.archiveType==null||this.archiveType.equals(ArchiveType.autodetect)) {
				archiverName = null;
			} else {
				archiverName = this.archiveType.name();
			}
			fileList = getFileDocumentsFromArchive(Core.getFileDocumentContent(getContext(), this.__archiveFile), archiverName);
			break;
		}
		FileUtils.deleteQuietly(tmpArchiveFile);
		return fileList;
		// END USER CODE
	}

	/**
	 * Returns a string representation of this action
	 */
	@Override
	public java.lang.String toString()
	{
		return "ExtractFile";
	}

	// BEGIN EXTRA CODE

	static ILogNode logNode = Core.getLogger(filesimportmodule.proxies.constants.Constants.getFilesImportLogNodeName());

	/**
	 * Create a temporary File with the contents of a file document.
	 * 
	 * @param fileDocument
	 * @return a File (which will be deleted when the VM exits)
	 * @throws IOException
	 */
	private File createTempFileForFileDoc(FileDocument fileDocument) throws IOException {
		logNode.debug("ExtractFile.createTempFileForFileDoc()");
		InputStream fileDocInputStream = Core.getFileDocumentContent(getContext(), fileDocument.getMendixObject());
		return createTempFileForInputStream(fileDocInputStream, fileDocument.getName());
	}

	/**
	 * Create a temporary File with the contents of an input stream. Because
	 * this is using FileUtils.copyInputStreamToFile, the stream will be closed.
	 * 
	 * @param inputStream
	 * @param name
	 * @return a File (which will be deleted when the VM exits)
	 * @throws IOException
	 */
	private File createTempFileForInputStream(InputStream inputStream, String name) throws IOException {
		logNode.debug(
				"ExtractFile.createTempFileForInputStream(" + inputStream.getClass().getName() + ", '" + name + "')");
		File archiveFile = File.createTempFile(name, ".extracting");
		FileUtils.copyInputStreamToFile(inputStream, archiveFile);
		archiveFile.deleteOnExit();
		return archiveFile;
	}
	
	private java.util.List<IMendixObject> getFileDocumentsFrom7zip(File tmpArchiveFile) throws IOException {
		// 7ZIP doesn't support streaming
		java.util.List<IMendixObject> fileList = new ArrayList<>();
		SevenZFile sevenZFile = new SevenZFile(tmpArchiveFile);
		try {
			SevenZArchiveEntry archiveEntry = sevenZFile.getNextEntry();
			while (archiveEntry!=null) {
				String entryName = archiveEntry.getName();
				if (archiveEntry.isDirectory()) {
					logNode.info("Archive entry " + entryName + " is not a file. Skipping.");
					continue;
				}
				byte[] fileBytes = new byte[(int) archiveEntry.getSize()];
				sevenZFile.read(fileBytes);
				InputStream entryInStr = new ByteArrayInputStream(fileBytes);
				FileDocument fileDoc = createFileDocument(entryInStr, archiveEntry);
				fileList.add(fileDoc.getMendixObject());
				archiveEntry = sevenZFile.getNextEntry();
			}
		} finally {
			sevenZFile.close();
		}
		return fileList;
	}

	private java.util.List<IMendixObject> getFileDocumentsFromArchive(InputStream inStream, String archiverName)
			throws ArchiveException, IOException {
		
//		TODO: In case of 7zip, do not use inputstream. Call getFileDocumentsFrom7zip(). How to detect?
		java.util.List<IMendixObject> fileList = new ArrayList<>();
		BufferedInputStream bfInStream = new BufferedInputStream(inStream);
		ArchiveInputStream archiveInputStream;
		if (archiverName != null) {
			archiveInputStream = new ArchiveStreamFactory().createArchiveInputStream(archiverName, bfInStream);
		} else {
			archiveInputStream = new ArchiveStreamFactory().createArchiveInputStream(bfInStream);
		}
		try {
			ArchiveEntry archiveEntry = archiveInputStream.getNextEntry();
			while (archiveEntry != null) {
				if (archiveInputStream.canReadEntryData(archiveEntry) && !archiveEntry.isDirectory()
						&& archiveEntry.getSize() >= 0) {
					// Create a bounded input stream for reading the current
					// file only
					BoundedInputStream boundedInStream = new BoundedInputStream(archiveInputStream,
							archiveEntry.getSize());
					try {
						boundedInStream.setPropagateClose(false); // Don't close
																	// the
																	// underling
																	// inputstream
						FileDocument fileDoc = createFileDocument(boundedInStream, archiveEntry);
						fileList.add(fileDoc.getMendixObject());
					} finally {
						IOUtils.closeQuietly(boundedInStream);
					}
				}
				archiveEntry = archiveInputStream.getNextEntry();
			}
		} finally {
			IOUtils.closeQuietly(archiveInputStream);
		}
		return fileList;
	}

	/**
	 * Create a new FileDocument and store the contents from an InputStream in
	 * it. The InputStream is always closed afterwards.
	 * 
	 * @param inputStream
	 * @param archiveEntry
	 * @return a new FileDocument, having the name of the archiveEntry
	 */
	private FileDocument createFileDocument(InputStream inputStream, ArchiveEntry archiveEntry) {
		logNode.debug("ExtractFile.createFileDocument(" + inputStream.getClass().getName() + ", archiveEntry<name=='"
				+ archiveEntry.getName() + "'>)");
		try {
			FileDocument fileDocument = new FileDocument(this.getContext());
			Core.storeFileDocumentContent(this.getContext(), fileDocument.getMendixObject(), archiveEntry.getName(),
					inputStream);
			return fileDocument;
		} finally {
			IOUtils.closeQuietly(inputStream);
		}
	}
	// END EXTRA CODE
}
