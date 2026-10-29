[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Jar)
$ErrorActionPreference='Stop'
if (-not ('JarLockOwners' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;
public static class JarLockOwners {
    [StructLayout(LayoutKind.Sequential)] public struct UniqueProcess { public uint Pid; public System.Runtime.InteropServices.ComTypes.FILETIME Started; }
    [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)] public struct ProcessInfo {
        public UniqueProcess Process;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst=256)] public string AppName;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst=64)] public string ServiceName;
        public uint AppType, Status, SessionId;
        [MarshalAs(UnmanagedType.Bool)] public bool Restartable;
    }
    [DllImport("rstrtmgr.dll", CharSet=CharSet.Unicode)] static extern int RmStartSession(out uint handle,uint flags,StringBuilder key);
    [DllImport("rstrtmgr.dll", CharSet=CharSet.Unicode)] static extern int RmRegisterResources(uint handle,uint files,string[] paths,uint apps,UniqueProcess[] processes,uint services,string[] serviceNames);
    [DllImport("rstrtmgr.dll")] static extern int RmGetList(uint handle,out uint needed,ref uint count,[In,Out]ProcessInfo[] infos,ref uint reasons);
    [DllImport("rstrtmgr.dll")] static extern int RmEndSession(uint handle);
    public static ProcessInfo[] Read(string file) {
        uint session; int error=RmStartSession(out session,0,new StringBuilder(33));
        if(error!=0)throw new Exception("RmStartSession="+error);
        try {
            error=RmRegisterResources(session,1,new[]{file},0,null,0,null); if(error!=0)throw new Exception("RmRegisterResources="+error);
            uint needed,count=0,reasons=0; error=RmGetList(session,out needed,ref count,null,ref reasons);
            if(error==0)return new ProcessInfo[0]; if(error!=234)throw new Exception("RmGetList="+error);
            ProcessInfo[] infos=new ProcessInfo[needed]; count=needed;
            error=RmGetList(session,out needed,ref count,infos,ref reasons);if(error!=0)throw new Exception("RmGetList="+error);
            Array.Resize(ref infos,(int)count);return infos;
        } finally { RmEndSession(session); }
    }
}
'@
}
$taskJar=(Resolve-Path -LiteralPath $Jar).Path
@([JarLockOwners]::Read($taskJar) | ForEach-Object {
    [ordered]@{ ProcessId=$_.Process.Pid; AppName=$_.AppName; AppType=$_.AppType; SessionId=$_.SessionId }
}) | ConvertTo-Json
