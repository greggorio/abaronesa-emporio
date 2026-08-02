#!/usr/bin/env python3
"""Nine exact probes using stdlib HTTP; secrets never enter argv or logs."""
import json,os,http.client
PROBES=("website_root","erp_root","website_theme_api","erp_login","erp_whatsapp_api","publisher_route_absent","deployer_route_absent","unknown_host_denied")
def request(port,host,path,method="GET",body=None,token=None):
 conn=http.client.HTTPConnection("127.0.0.1",port,timeout=15);headers={"Host":host}
 if body is not None:headers["Content-Type"]="application/json"
 if token:headers["Authorization"]="Bearer "+token
 conn.request(method,path,body=json.dumps(body) if body is not None else None,headers=headers);response=conn.getresponse();data=response.read();conn.close();return response.status,data
def run():
 port=int(os.environ["CANDIDATE_GATEWAY_PORT"]);erp="erp-emporio.abaronesa.net.br";web="emporio.abaronesa.net.br";passed=[]
 for pid,host,path in (("website_root",web,"/"),("erp_root",erp,"/")):
  status,data=request(port,host,path)
  if status!=200 or not data:raise ValueError(pid)
  passed.append({"id":pid,"status":"passed"})
 status,data=request(port,web,"/api/themes?tenantId=candidate.invalid")
 if status!=200 or not isinstance(json.loads(data),list):raise ValueError("website_theme_api")
 passed.append({"id":"website_theme_api","status":"passed"})
 status,data=request(port,erp,"/api/auth/login","POST",{"email":os.environ["CANDIDATE_ROOT_EMAIL"],"password":os.environ["CANDIDATE_ROOT_PASSWORD"]})
 token=json.loads(data).get("accessToken") if status==200 else None
 if not isinstance(token,str) or not token:raise ValueError("erp_login")
 passed.append({"id":"erp_login","status":"passed"})
 status,data=request(port,erp,"/api/whatsapp/status",token=token)
 if status!=200 or not isinstance(json.loads(data),dict):raise ValueError("erp_whatsapp_api")
 passed.append({"id":"erp_whatsapp_api","status":"passed"})
 # Autenticado de proposito: sem token o Spring Security responde 401 antes de
 # rotear, e 401 nao distingue rota ausente de rota existente. Com token valido,
 # 404 prova que a rota nao existe nesta imagem.
 for pid,path in (("publisher_route_absent","/api/release-publisher/v1/candidates"),("deployer_route_absent","/api/deployment-control/v1/current")):
  status,_=request(port,erp,path,token=token)
  if status!=404:raise ValueError(pid)
  passed.append({"id":pid,"status":"passed"})
 try:status,_=request(port,"unknown.invalid","/")
 except (ConnectionError,http.client.HTTPException):status=0
 if 200<=status<300:raise ValueError("unknown_host_denied")
 passed.append({"id":"unknown_host_denied","status":"passed"});return passed
