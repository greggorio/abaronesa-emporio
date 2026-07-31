import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Upload, X } from 'lucide-react';
import villaApi from '@/services/villaApi';

interface ThemeAndroidFormProps {
  assets: Record<string, any>;
  setAssets: (assets: Record<string, any>) => void;
  assetsStr: string;
  setAssetsStr: (assetsStr: string) => void;
  content: Record<string, any>;
  setContent: (content: Record<string, any>) => void;
  contentStr: string;
  setContentStr: (contentStr: string) => void;
}

export function ThemeAndroidForm({
  assets,
  setAssets,
  assetsStr,
  setAssetsStr,
  content,
  setContent,
  contentStr,
  setContentStr
}: ThemeAndroidFormProps) {
  const [uploading, setUploading] = useState<Record<string, boolean>>({});

  const androidAssets = (assets.android ?? {}) as Record<string, any>;
  const androidContent = (content.android ?? {}) as Record<string, any>;

  const updateAndroidAssets = (key: string, value: string) => {
    const updatedAndroidAssets = { ...androidAssets, [key]: value };
    const updatedAssets = { ...assets, android: updatedAndroidAssets };
    setAssets(updatedAssets);
    setAssetsStr(JSON.stringify(updatedAssets, null, 2));
  };

  const updateAndroidContent = (key: string, value: any) => {
    const updatedAndroidContent = { ...androidContent, [key]: value };
    const updatedContent = { ...content, android: updatedAndroidContent };
    setContent(updatedContent);
    setContentStr(JSON.stringify(updatedContent, null, 2));
  };

  const updateAndroidContentBulk = (values: Record<string, any>) => {
    const updatedAndroidContent = { ...androidContent, ...values };
    const updatedContent = { ...content, android: updatedAndroidContent };
    setContent(updatedContent);
    setContentStr(JSON.stringify(updatedContent, null, 2));
  };

  const removeAndroidAsset = (key: string) => {
    const updatedAndroidAssets = { ...androidAssets };
    delete updatedAndroidAssets[key];
    const updatedAssets = { ...assets, android: updatedAndroidAssets };
    setAssets(updatedAssets);
    setAssetsStr(JSON.stringify(updatedAssets, null, 2));
  };

  const handleUpload = async (key: string, type: string, file: File) => {
    if (!file) return;

    setUploading(prev => ({ ...prev, [key]: true }));

    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('type', type);

      const response = await villaApi.post('/api/themes/android/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      if (response.data?.success) {
        const value = response.data.url || response.data.path;
        if (value) {
          updateAndroidAssets(key, value);
        }
        if (type === 'googleServices' && response.data.packageName) {
          const packageName = response.data.packageName;
          updateAndroidContentBulk({
            packageName,
            applicationId: packageName,
            namespace: packageName,
            customUrlScheme: packageName
          });
        }
      }
    } catch (error) {
      console.error('Erro ao fazer upload Android:', error);
    } finally {
      setUploading(prev => ({ ...prev, [key]: false }));
    }
  };

  return (
    <div className="space-y-6">
      <div className="border border-[#8B7355]/30 rounded-lg p-4">
        <h3 className="text-lg font-medium text-[#2A1F1B] mb-3">Configuracao do App</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="androidAppName" className="text-sm text-[#2A1F1B]">Nome do App</Label>
            <Input
              id="androidAppName"
              value={androidContent.appName || ''}
              onChange={(e) => updateAndroidContent('appName', e.target.value)}
              className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
              placeholder="Ex: Espresso App"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="androidTitleActivityMain" className="text-sm text-[#2A1F1B]">Titulo da Activity</Label>
            <Input
              id="androidTitleActivityMain"
              value={androidContent.titleActivityMain || ''}
              onChange={(e) => updateAndroidContent('titleActivityMain', e.target.value)}
              className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
              placeholder="Ex: Espresso App"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="androidPackageName" className="text-sm text-[#2A1F1B]">Package Name (strings)</Label>
            <Input
              id="androidPackageName"
              value={androidContent.packageName || ''}
              onChange={(e) => updateAndroidContent('packageName', e.target.value)}
              readOnly
              className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
              placeholder="Ex: com.empresa.app"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="androidCustomUrlScheme" className="text-sm text-[#2A1F1B]">Custom URL Scheme</Label>
            <Input
              id="androidCustomUrlScheme"
              value={androidContent.customUrlScheme || ''}
              onChange={(e) => updateAndroidContent('customUrlScheme', e.target.value)}
              readOnly
              className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
              placeholder="Ex: com.empresa.app"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="androidApplicationId" className="text-sm text-[#2A1F1B]">Application ID</Label>
            <Input
              id="androidApplicationId"
              value={androidContent.applicationId || ''}
              onChange={(e) => updateAndroidContent('applicationId', e.target.value)}
              readOnly
              className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
              placeholder="Ex: com.empresa.app"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="androidNamespace" className="text-sm text-[#2A1F1B]">Namespace</Label>
            <Input
              id="androidNamespace"
              value={androidContent.namespace || ''}
              onChange={(e) => updateAndroidContent('namespace', e.target.value)}
              readOnly
              className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
              placeholder="Ex: com.empresa.app"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="androidVersionName" className="text-sm text-[#2A1F1B]">Version Name</Label>
            <Input
              id="androidVersionName"
              value={androidContent.versionName || ''}
              onChange={(e) => updateAndroidContent('versionName', e.target.value)}
              className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
              placeholder="Ex: 1.0.0"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="androidVersionCode" className="text-sm text-[#2A1F1B]">Version Code</Label>
            <Input
              id="androidVersionCode"
              type="number"
              value={androidContent.versionCode ?? ''}
              onChange={(e) => {
                const value = e.target.value;
                updateAndroidContent('versionCode', value === '' ? '' : Number(value));
              }}
              className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
              placeholder="Ex: 1"
            />
          </div>
        </div>
      </div>

      <div className="border border-[#8B7355]/30 rounded-lg p-4 space-y-4">
        <h3 className="text-lg font-medium text-[#2A1F1B]">Arquivos Android</h3>

        <div className="space-y-2">
          <Label className="text-sm text-[#2A1F1B]">google-services.json</Label>
          <div className="flex items-center gap-4">
            {androidAssets.googleServicesJsonUrl && (
              <div className="relative flex items-center gap-2 text-xs text-[#8B7355]">
                <a href={androidAssets.googleServicesJsonUrl} target="_blank" rel="noreferrer" className="underline">
                  Visualizar arquivo atual
                </a>
                <button
                  type="button"
                  onClick={() => removeAndroidAsset('googleServicesJsonUrl')}
                  className="text-red-600 hover:text-red-700"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            )}
            <div className="flex-1">
              <Input
                type="file"
                accept=".json,application/json"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) {
                    handleUpload('googleServicesJsonUrl', 'googleServices', file);
                  }
                }}
                className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                disabled={uploading.googleServicesJsonUrl}
              />
              {uploading.googleServicesJsonUrl && (
                <p className="text-xs text-muted-foreground mt-1">Fazendo upload...</p>
              )}
            </div>
          </div>
        </div>

        <div className="space-y-2">
          <Label className="text-sm text-[#2A1F1B]">firebase-adminsdk.json (privado)</Label>
          <div className="flex items-center gap-4">
            {androidAssets.firebaseAdminsdkPath && (
              <div className="relative flex items-center gap-2 text-xs text-[#8B7355]">
                <span className="truncate max-w-[240px]">{androidAssets.firebaseAdminsdkPath}</span>
                <button
                  type="button"
                  onClick={() => removeAndroidAsset('firebaseAdminsdkPath')}
                  className="text-red-600 hover:text-red-700"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            )}
            <div className="flex-1">
              <Input
                type="file"
                accept=".json,application/json"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) {
                    handleUpload('firebaseAdminsdkPath', 'firebaseAdmin', file);
                  }
                }}
                className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                disabled={uploading.firebaseAdminsdkPath}
              />
              {uploading.firebaseAdminsdkPath && (
                <p className="text-xs text-muted-foreground mt-1">Fazendo upload...</p>
              )}
            </div>
          </div>
        </div>

        <div className="space-y-2">
          <Label className="text-sm text-[#2A1F1B]">Icones (zip)</Label>
          <div className="flex items-center gap-4">
            {androidAssets.iconsZipUrl && (
              <div className="relative flex items-center gap-2 text-xs text-[#8B7355]">
                <a href={androidAssets.iconsZipUrl} target="_blank" rel="noreferrer" className="underline">
                  Download do zip
                </a>
                <button
                  type="button"
                  onClick={() => removeAndroidAsset('iconsZipUrl')}
                  className="text-red-600 hover:text-red-700"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            )}
            <div className="flex-1">
              <Input
                type="file"
                accept=".zip,application/zip"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) {
                    handleUpload('iconsZipUrl', 'iconsZip', file);
                  }
                }}
                className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                disabled={uploading.iconsZipUrl}
              />
              {uploading.iconsZipUrl && (
                <p className="text-xs text-muted-foreground mt-1">Fazendo upload...</p>
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="flex items-center gap-2 text-xs text-[#8B7355]/70">
        <Upload className="w-4 h-4" />
        <span>Os arquivos enviados ficam vinculados ao tema ativo.</span>
      </div>
    </div>
  );
}
