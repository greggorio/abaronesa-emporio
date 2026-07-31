#!/usr/bin/env python3
"""Attempt every directed build-image cleanup and fail only after all attempts."""
import argparse,subprocess,sys

def cleanup(reference,runner=subprocess.run):
 errors=[]
 def invoke(name,args,absence=False):
  try:result=runner(args,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  except Exception:
   errors.append(name+":error")
   return
  if (absence and result.returncode==0) or (not absence and result.returncode!=0):errors.append(name)
 invoke("logout",["docker","logout","ghcr.io"])
 invoke("remove",["docker","image","rm",reference])
 invoke("image-present",["docker","image","inspect",reference],absence=True)
 return errors

def main(argv=None):
 parser=argparse.ArgumentParser();parser.add_argument("--reference",required=True);args=parser.parse_args(argv)
 errors=cleanup(args.reference)
 if errors:
  print("image-cleanup:invalid:"+",".join(errors),file=sys.stderr)
  return 3
 print("image-cleanup:valid")
 return 0

if __name__=="__main__":raise SystemExit(main())
