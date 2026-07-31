#!/usr/bin/env python3
"""Safe artifact transport and atomic strict JSON bundles."""
import hashlib,json,os,re,subprocess,tempfile,zipfile
from pathlib import Path,PurePosixPath
MAX_ZIP=2*1024*1024
LIMITS={"candidate.json":1024*1024,"candidate.json.sha256":128,"metadata.json":16*1024}
def canonical(value):return (json.dumps(value,sort_keys=True,separators=(",",":"),ensure_ascii=False)+"\n").encode()
def digest(data):return "sha256:"+hashlib.sha256(data).hexdigest()
def sidecar(data):return hashlib.sha256(data).hexdigest().encode()+b"\n"
def verify_pair(path):
 try:return path.with_name(path.name+".sha256").read_bytes()==sidecar(path.read_bytes())
 except OSError:return False
def gh_download(repository,artifact_id,target):
 if repository!="greggorio/abaronesa-emporio" or not re.fullmatch(r"[1-9][0-9]*",str(artifact_id)):raise ValueError("artifact endpoint identity")
 endpoint=f"/repos/{repository}/actions/artifacts/{artifact_id}/zip"
 with target.open("wb") as stream:
  process=subprocess.run(["gh","api",endpoint],stdout=stream,stderr=subprocess.PIPE)
 if process.returncode:raise subprocess.CalledProcessError(process.returncode,process.args,stderr=process.stderr)
 return endpoint
def safe_extract(archive,destination,expected_digest):
 raw=archive.read_bytes()
 if len(raw)>MAX_ZIP or digest(raw)!=expected_digest:raise ValueError("artifact zip size or digest")
 with zipfile.ZipFile(archive) as bundle:
  infos=bundle.infolist()
  names=[i.filename for i in infos]
  if len(names)!=len(set(names)) or set(names)!=set(LIMITS):raise ValueError("artifact entries")
  destination.mkdir(parents=True,exist_ok=True)
  for info in infos:
   path=PurePosixPath(info.filename)
   if info.is_dir() or path.is_absolute() or ".." in path.parts or info.file_size>LIMITS[info.filename]:raise ValueError("unsafe artifact entry")
   data=bundle.read(info)
   if len(data)!=info.file_size:raise ValueError("artifact entry size")
   (destination/info.filename).write_bytes(data)
 if not verify_pair(destination/"candidate.json"):raise ValueError("artifact sidecar")
def safe_extract_named(archive,destination,expected_digest,limits,pair_name):
 raw=archive.read_bytes()
 if len(raw)>MAX_ZIP or digest(raw)!=expected_digest:raise ValueError("artifact zip size or digest")
 with zipfile.ZipFile(archive) as bundle:
  infos=bundle.infolist();names=[i.filename for i in infos]
  if len(names)!=len(set(names)) or set(names)!=set(limits):raise ValueError("artifact entries")
  destination.mkdir(parents=True,exist_ok=True)
  for info in infos:
   path=PurePosixPath(info.filename)
   if info.is_dir() or path.is_absolute() or ".." in path.parts or info.file_size>limits[info.filename]:raise ValueError("unsafe artifact entry")
   (destination/info.filename).write_bytes(bundle.read(info))
 if not verify_pair(destination/pair_name):raise ValueError("artifact sidecar")
def _stage(directory,name,data):
 fd,raw=tempfile.mkstemp(prefix="."+name+".",dir=directory)
 with os.fdopen(fd,"wb") as stream:stream.write(data);stream.flush();os.fsync(stream.fileno())
 return Path(raw)
def atomic_bundle(directory,files):
 directory.mkdir(parents=True,exist_ok=True)
 if any((directory/name).exists() for name in files):raise ValueError("bundle exists")
 staged={};committed=[]
 try:
  for name,data in files.items():staged[name]=_stage(directory,name,data)
  for name,path in staged.items():os.replace(path,directory/name);committed.append(name)
  fd=os.open(directory,os.O_RDONLY|getattr(os,"O_DIRECTORY",0));os.fsync(fd);os.close(fd)
  if any((directory/name).read_bytes()!=data for name,data in files.items()):raise ValueError("post-write verification")
 except Exception:
  for path in staged.values():path.unlink(missing_ok=True)
  for name in committed:(directory/name).unlink(missing_ok=True)
  raise
