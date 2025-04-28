const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  sendCommand: (data) => ipcRenderer.invoke('run-command', data),
  selectProjectFolder: () => ipcRenderer.invoke('select-folder'),
});
