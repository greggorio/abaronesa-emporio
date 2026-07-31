import sys, unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from validate_gateway import validate,ROOT

class GatewayTests(unittest.TestCase):
    def setUp(self):
        b=ROOT/"ops/gateway"
        self.parts=[(b/"Dockerfile").read_text(),(b/"nginx.conf").read_text(),
          (b/"conf.d/emporio.conf").read_text(),(b/"proxy-common.conf").read_text(),
          (b/"proxy-websocket.conf").read_text()]
    def test_valid(self): validate(*self.parts)
    def test_independent_mutants(self):
        mutations=[
          (0,"USER 101:101","USER 0:0","non-root"),
          (0,"EXPOSE 8080","EXPOSE 80","port"),
          (2,"return 444","return 200","default host"),
          (2,"backend:8080","backend:9999","ERP upstream"),
          (2,"website_back:8085","website_back:9999","website upstream"),
          (2,"location = /ws","location = /socket","exact ws"),
          (2,"client_max_body_size 10m","client_max_body_size 100m","ERP body"),
          (2,"client_max_body_size 2m","client_max_body_size 100m","website body"),
          (2,"X-Frame-Options","X-Removed-Frame","security headers"),
          (2,"location ^~ /api/deployment-control/","location ^~ /deployment/","control route"),
          (2,"location ^~ /api/","location ^~ /rest/","api route"),
          (3,"proxy_connect_timeout 5s","proxy_connect_timeout 0","timeouts"),
          (3,"X-Forwarded-Proto $forwarded_proto","X-Forwarded-Proto $http_x_forwarded_proto","proto header"),
          (4,"Upgrade $http_upgrade","Upgrade off","websocket upgrade"),
          (1,"'' $scheme","'' ''","proto fallback"),
        ]
        for index,old,new,label in mutations:
            with self.subTest(label=label):
                parts=self.parts.copy(); parts[index]=parts[index].replace(old,new,1)
                with self.assertRaises(ValueError): validate(*parts)
    def test_forbidden_extra_routes(self):
        parts=self.parts.copy()
        parts[2]=parts[2].replace("location ^~ /media/", "location ^~ /actuator/ { proxy_pass http://erp_backend; }\n  location ^~ /media/",1)
        with self.assertRaises(ValueError): validate(*parts)
    def test_direct_whatsapp_mutant(self):
        parts=self.parts.copy(); parts[2]+="\nupstream direct { server whatsapp_service:3001; }\n"
        with self.assertRaises(ValueError): validate(*parts)
if __name__=="__main__": unittest.main()
