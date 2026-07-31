import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import SignageNoticeAdmin from './SignageNoticeAdmin';
import SignageScheduleAdmin from './SignageScheduleAdmin';
import SignageDevicesAdmin from './SignageDevicesAdmin';
import SignageSyncGroupsAdmin from './SignageSyncGroupsAdmin';
import SignageScenesAdmin from './SignageScenesAdmin';

const SignageAdmin = () => {
  return (
    <div className="h-full bg-[#FBF6F2] flex flex-col">
      <div className="bg-white shadow-sm p-4 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <h1 className="text-xl font-display text-[#2A1F1B]">Signage</h1>
          <span className="text-[#8B7355]/70">{'>'}</span>
          <span className="text-[#8B7355]/70 capitalize">Painel</span>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-4">
        <Tabs defaultValue="notice" className="w-full">
          <TabsList className="grid w-full grid-cols-5 bg-[#F5EDE6] border border-[#D7B899]/60 rounded-lg p-1">
            <TabsTrigger value="notice" className="data-[state=active]:bg-[#D7B899] data-[state=active]:text-[#2A1F1B] text-[#8B7355]">
              Mídias
            </TabsTrigger>
            <TabsTrigger value="schedules" className="data-[state=active]:bg-[#D7B899] data-[state=active]:text-[#2A1F1B] text-[#8B7355]">
              Agenda/Playlists
            </TabsTrigger>
            <TabsTrigger value="devices" className="data-[state=active]:bg-[#D7B899] data-[state=active]:text-[#2A1F1B] text-[#8B7355]">
              Dispositivos
            </TabsTrigger>
            <TabsTrigger value="sync" className="data-[state=active]:bg-[#D7B899] data-[state=active]:text-[#2A1F1B] text-[#8B7355]">
              Sync Groups
            </TabsTrigger>
            <TabsTrigger value="scenes" className="data-[state=active]:bg-[#D7B899] data-[state=active]:text-[#2A1F1B] text-[#8B7355]">
              Scenes
            </TabsTrigger>
          </TabsList>

          <TabsContent value="notice" className="mt-4">
            <SignageNoticeAdmin />
          </TabsContent>
          <TabsContent value="schedules" className="mt-4">
            <SignageScheduleAdmin />
          </TabsContent>
          <TabsContent value="devices" className="mt-4">
            <SignageDevicesAdmin />
          </TabsContent>
          <TabsContent value="sync" className="mt-4">
            <SignageSyncGroupsAdmin />
          </TabsContent>
          <TabsContent value="scenes" className="mt-4">
            <SignageScenesAdmin />
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
};

export default SignageAdmin;
