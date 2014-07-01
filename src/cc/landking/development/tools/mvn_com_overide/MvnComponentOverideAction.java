package cc.landking.development.tools.mvn_com_overide;

import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.eclipse.core.internal.resources.Folder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJarEntryResource;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.JarEntryDirectory;
import org.eclipse.jdt.internal.core.JarEntryFile;
import org.eclipse.jdt.internal.core.JarPackageFragmentRoot;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.PackageFragment;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.m2e.core.internal.builder.MavenNature;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.json.JSONObject;
import org.json.JSONTokener;

public class MvnComponentOverideAction implements IObjectActionDelegate {
	IProject project;
	
	static Map<String,String> artifactCache = new HashMap<String,String>();

	private static void addFiles(Map<String, List<String>> groups,
			IPackageFragmentRoot sourceRoot, List<IPackageFragmentRoot> jars)
			throws Exception {
		IJavaElement[] javaElements = sourceRoot.getChildren();
		for (int i = 0; i < javaElements.length; i++) {
			addFile(groups, javaElements[i], jars);
		}

		IResource resource = sourceRoot.getResource();
		addResourceFile(sourceRoot, groups, resource, jars);
	}

	private static void addResourceFile(IPackageFragmentRoot sourceRoot,
			Map<String, List<String>> groups, IResource resource,
			List<IPackageFragmentRoot> jars) throws Exception {
		if (resource instanceof Folder) {
			Folder folder = (Folder) resource;
			IResource[] resources = folder.members();
			for (int i = 0; i < resources.length; i++) {
				addResourceFile(sourceRoot, groups, resources[i], jars);
			}
		} else if (resource instanceof org.eclipse.core.internal.resources.File) {
			org.eclipse.core.internal.resources.File theFile = (org.eclipse.core.internal.resources.File) resource;
			if (theFile.getFileExtension().equals("java")) {
				return;
			}
			String resourcePathString = resource
					.getLocation()
					.toString()
					.substring(
							sourceRoot.getResource().getLocation().toString()
									.length()).replace('/', '.');
			String path =  resource
					.getLocation()
					.toString()
					.substring(
							sourceRoot.getResource().getLocation().toString()
									.length());
			System.out.println("addResourceFile,sourceRoot: "
					+ sourceRoot.getResource().getLocation().toString()
					+ " resource:" + resourcePathString);
			for (IPackageFragmentRoot root : jars) {
				IJavaElement[] javaElements = root.getChildren();
				JarPackageFragmentRoot jroot = (JarPackageFragmentRoot) root;

				for (int i = 0; i < javaElements.length; i++) {
					IJavaElement el = javaElements[i];

					// System.out.println("element name:" + el.getElementName()
					// + "| " + className);
					if (resourcePathString.contains(el.getElementName())) {
						if (el instanceof IPackageFragment) {
							PackageFragment jpf = (PackageFragment) el;
							Object[] objects = jpf.getNonJavaResources();
							for(int j=0;j<objects.length;j++){
								if(objects[j] instanceof JarEntryFile){
									JarEntryFile file = (JarEntryFile)objects[j];
									addJarEntryFile(groups,file,jroot,path);
//									System.out.println("55555555555:"+file.getName()+" path:"+file.getFullPath());
								}else{
									System.out.println("55555555555:"+objects[j].getClass());
								}
							}

						}else{
							System.out.println("non IPackageFragment:" + resourcePathString
									+ " el::"
									+ el.getElementName());
						}
					}
				}
			}
		}
	}
	
	public static void setArtifactId(Map<String, List<String>> groups,String fileName,String path) throws Exception{
		System.out.println("got! filename:"+fileName+" path:"+path);
		String artifactId = artifactCache.get(fileName);
		if(artifactId == null){
			artifactId = getArtifactId(fileName);
			artifactCache.put(fileName, artifactId);
		}
		List<String> ov = groups.get(artifactId);
		if(ov == null){
			ov = new ArrayList<String>();
			groups.put(artifactId, ov);
		}
		if(path.startsWith("/")){
			path = path.substring(1);
		}
		ov.add(path);
	}
	public static String getArtifactId(String fileName) throws Exception{
		JSONObject jb = getArtifactByNet(fileName);
		JSONObject doc = (JSONObject) jb.getJSONObject("response").getJSONArray("docs").get(0);
		return doc.getString("id");
	}

	public static JSONObject getArtifactByNet(String fileName) {  
		
		String url = "http://search.maven.org/solrsearch/select?rows=20&wt=json&q="+fileName.substring(0,fileName.length()-4);
        
        HttpClient client = new DefaultHttpClient();  
        HttpGet get = new HttpGet(url);  
        JSONObject json = null;  
        try {  
            HttpResponse res = client.execute(get);  
            if (res.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {  
                HttpEntity entity = res.getEntity();  
                json = new JSONObject(new JSONTokener(new InputStreamReader(entity.getContent(), HTTP.UTF_8)));  
            }  
        } catch (Exception e) {  
            throw new RuntimeException(e);  
              
        } finally{  
            //关闭连接 ,释放资源  
            client.getConnectionManager().shutdown();  
        }  
        return json;  
    }  
	
	@SuppressWarnings("restriction")
	private static void addJarEntryFile(Map<String, List<String>> groups,
			Object object, JarPackageFragmentRoot jar, String path)
	throws Exception {
		if(object instanceof JarEntryFile){
			JarEntryFile file = (JarEntryFile)object;
//			System.out.println("55555555555:"+file.getName()+" path:"+file.getFullPath());
			if(file.getFullPath().toString().equals(path)){
//				System.out.println("hahaha got it:"+file.getFullPath() + " jar:"+ new File(jar.getJar().getName()).getName());
				setArtifactId(groups,new File(jar.getJar().getName()).getName(),path);
			}
		}else if(object instanceof JarEntryDirectory){
			JarEntryDirectory directory = (JarEntryDirectory)object;
			IJarEntryResource[] resources = directory.getChildren();
			for(int i=0;i<resources.length;i++){
				IJarEntryResource resource = resources[i];
				addJarEntryFile(groups,resource,jar,path);
			}
		}

	}
	
	private static void addFile(Map<String, List<String>> groups,
			IJavaElement iJavaElement, List<IPackageFragmentRoot> jars)
			throws Exception {
		if (iJavaElement instanceof ICompilationUnit) {
			//
			ICompilationUnit cu = (ICompilationUnit) iJavaElement;

			String className = cu.getTypes()[0].getFullyQualifiedName();

			for (IPackageFragmentRoot root : jars) {
				IJavaElement[] javaElements = root.getChildren();
				JarPackageFragmentRoot jroot = (JarPackageFragmentRoot) root;

				for (int i = 0; i < javaElements.length; i++) {
					IJavaElement el = javaElements[i];

					// System.out.println("element name:" + el.getElementName()
					// + "| " + className);
					if (className.contains(el.getElementName())) {
						if (el instanceof IPackageFragment) {
							IPackageFragment jpf = (IPackageFragment) el;
							jpf.getClassFiles();
							IJavaElement[] jel = jpf.getChildren();
							for (int j = 0; j < jel.length; j++) {
								IJavaElement el1 = jel[j];

								if ((className + ".class").equals(el
										.getElementName()
										+ "."
										+ el1.getElementName())) {
//									System.out.println("got it!" + className
//											+ " in jar:"
//											+ jroot.getJar().getName());
									setArtifactId(groups,new File(jroot.getJar().getName()).getName(),className.replace('.', '/')+".class");
								}
							}
						}
					}
				}
			}


//			System.out.println("addFile:"
//					+ cu.getTypes()[0].getFullyQualifiedName() + "|"
//					+ cu.getElementName());
		} else if (iJavaElement instanceof IPackageFragment) {
			IPackageFragment pf = (IPackageFragment) iJavaElement;
			IJavaElement[] javaElements = pf.getChildren();
			for (int i = 0; i < javaElements.length; i++) {
				addFile(groups, javaElements[i], jars);
			}
		} else if (iJavaElement instanceof IJarEntryResource) {
			IJarEntryResource resource = (IJarEntryResource) iJavaElement;
			System.out.println("addFile:resource " + iJavaElement.getResource()
					+ "|" + resource.getName());
		} else {
			System.out.println("addFile:resource " + iJavaElement.getResource()
					+ "|" + iJavaElement.getElementName() + "|"
					+ iJavaElement.getClass());
		}

	}

	

	@Override
	public void run(IAction action) {
		final Shell shell = PlatformUI.getWorkbench()
				.getActiveWorkbenchWindow().getShell();
		IRunnableWithProgress runnableWithProgress = new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor)
					throws InvocationTargetException, InterruptedException {
				try {

					// MessageDialog.openInformation(shell, "Hello Eclipse",
					// "Hello Eclipse.");
					IJavaProject javaProject = null;

					// ServerElement serverElement = new ServerElement();

					IProjectNature nature = project
							.getNature(JavaCore.NATURE_ID);
					IProjectNature mvnNature = project
							.getNature("org.eclipse.m2e.core.maven2Nature");
					if (mvnNature != null) {
						System.out.println(mvnNature.getClass());
						MavenNature mavenNature = (MavenNature) mvnNature;

					}
					if (nature != null) {

						javaProject = (JavaProject) nature;
						File target = javaProject.getResource().getLocation()
								.toFile();
						Map<String, List<String>> groups = new HashMap<String, List<String>>();
						IPackageFragmentRoot[] roots = javaProject
								.getPackageFragmentRoots();
						List<IPackageFragmentRoot> jars = new ArrayList<IPackageFragmentRoot>();
						List<IPackageFragmentRoot> sources = new ArrayList<IPackageFragmentRoot>();

						for (int i = 0; i < roots.length; i++) {
							IPackageFragmentRoot root = roots[i];
							if ((root.getKind() == IPackageFragmentRoot.K_SOURCE)) {
								System.out.println("kind:"
										+ roots[i].getKind()
										+ " name:"
										+ roots[i].getElementName()
										+ " full path:"
										+ roots[i].getCorrespondingResource()
												.getFullPath()
										+ " getLocation:"
										+ root.getResource().getLocation());

								sources.add(root);
							} else {
								System.out.println("kind:" + roots[i].getKind()
										+ " name:" + roots[i].getElementName()
										+ " full path:" + roots[i].getPath()
										+ " getLocation:" + root.getResource());
								jars.add(root);
								IJavaElement[] javaElements = root
										.getChildren();
							}
						}
						for (IPackageFragmentRoot root : sources) {
							addFiles(groups, root, jars);
						}

						printToConsole(groups);
					} else {
						// throw new Exception();
						MessageDialog
								.openInformation(
										shell,
										"Error",
										Messages.getString("WTPenviromentCreator.notjavaproject"));
					}

					// serverElement.start();
				} catch (Exception e) {

					e.printStackTrace();
					Status status = new Status(
							IStatus.ERROR,
							Activator.PLUGIN_ID,
							IStatus.OK,
							Messages.getString("BaseAction.action.errors.status.message") + ".", e); //$NON-NLS-1$ //$NON-NLS-2$
					Activator.getDefault().getLog().log(status);
					MessageDialog
							.openInformation(
									shell,
									"Error",
									Messages.getString("WTPenviromentCreator.notjavaproject")
											+ e.getMessage());
				}
			}
		};
		ProgressMonitorDialog dialog = new ProgressMonitorDialog(PlatformUI
				.getWorkbench().getActiveWorkbenchWindow().getShell());

		try {
			dialog.run(true, true, runnableWithProgress);
		} catch (Exception e) {
			MessageDialog.openInformation(shell, "Error",
					Messages.getString("WTPenviromentCreator.notjavaproject")
							+ e.getMessage());
			e.printStackTrace();
		}

	}
	private static void printToConsole(Map<String,List<String>> groups){
		StringBuffer sb = new StringBuffer();
		sb.append(" <plugin><groupId>org.apache.maven.plugins</groupId> <artifactId>maven-shade-plugin</artifactId> <version>2.3</version> <executions> <execution> <phase>package</phase> <goals> <goal>shade</goal> </goals> <configuration> <filters>");
		for(String key :groups.keySet()){
			sb.append(" <filter> <artifact>"+key+"</artifact> <excludes>");
			for(String path:groups.get(key)){
				sb.append("<exclude>"+path+"</exclude>");
			}
			sb.append("</excludes></filter>");
		}
		sb.append(" </filters></configuration></execution></executions> </plugin>");
		MessageConsole console = new MessageConsole("Copy Console Content to pom.xml project/build/plugins node", null);
		 
		 // 通过ConsolePlugin得到ConsoleManager，并添加新的MessageConsole
		 ConsolePlugin.getDefault().getConsoleManager().addConsoles(new IConsole[] { console });
		 
		 // 新建一个MessageConsoleStream
		 MessageConsoleStream consoleStream = console.newMessageStream();
		 
		// 使用MessageConsoleStream来打印信息到Console View
		consoleStream.println(sb.toString());
	}
	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		StructuredSelection structureSelection = (StructuredSelection) selection;
		project = (IProject) structureSelection.getFirstElement();

	}

	@Override
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {

	}
	public static void main(String[] args) throws Exception{
		String fileName = "activiti-engine-5.15.1.jar";
		
		System.out.println(getArtifactId(fileName));
	}

}
