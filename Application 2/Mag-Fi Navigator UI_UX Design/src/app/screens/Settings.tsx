import { useState } from 'react';
import { useNavigate } from 'react-router';
import { motion } from 'motion/react';
import {
  ChevronLeft,
  ChevronRight,
  Compass,
  Sliders,
  Palette,
  Info,
  FileText,
  Download,
  Map,
  Vibrate,
  Volume2,
  Bell,
  Moon,
  Sun,
} from 'lucide-react';
import { Switch } from '../components/ui/switch';
import { Slider } from '../components/ui/slider';

export default function Settings() {
  const navigate = useNavigate();
  const [soundEnabled, setSoundEnabled] = useState(true);
  const [vibrationEnabled, setVibrationEnabled] = useState(true);
  const [notificationsEnabled, setNotificationsEnabled] = useState(true);
  const [darkMode, setDarkMode] = useState(true);
  const [sensorSensitivity, setSensorSensitivity] = useState([75]);

  const settingsSections = [
    {
      title: 'Navigation',
      items: [
        {
          icon: Compass,
          label: 'Calibrate Compass',
          description: 'Recalibrate magnetometer',
          action: 'navigate',
          color: 'text-cyan-400',
        },
        {
          icon: Map,
          label: 'Offline Maps',
          description: 'Manage downloaded maps',
          action: 'navigate',
          color: 'text-blue-400',
        },
      ],
    },
    {
      title: 'Sensors',
      items: [
        {
          icon: Sliders,
          label: 'Sensor Sensitivity',
          description: 'Adjust sensor precision',
          action: 'slider',
          color: 'text-purple-400',
        },
      ],
    },
    {
      title: 'Preferences',
      items: [
        {
          icon: Volume2,
          label: 'Sound',
          description: 'Navigation audio cues',
          action: 'toggle',
          state: soundEnabled,
          setState: setSoundEnabled,
          color: 'text-green-400',
        },
        {
          icon: Vibrate,
          label: 'Vibration',
          description: 'Haptic feedback',
          action: 'toggle',
          state: vibrationEnabled,
          setState: setVibrationEnabled,
          color: 'text-yellow-400',
        },
        {
          icon: Bell,
          label: 'Notifications',
          description: 'App notifications',
          action: 'toggle',
          state: notificationsEnabled,
          setState: setNotificationsEnabled,
          color: 'text-red-400',
        },
        {
          icon: darkMode ? Moon : Sun,
          label: 'Theme',
          description: darkMode ? 'Dark mode' : 'Light mode',
          action: 'toggle',
          state: darkMode,
          setState: setDarkMode,
          color: 'text-indigo-400',
        },
      ],
    },
    {
      title: 'Data',
      items: [
        {
          icon: FileText,
          label: 'Export Logs',
          description: 'Download sensor data',
          action: 'navigate',
          color: 'text-orange-400',
        },
      ],
    },
    {
      title: 'About',
      items: [
        {
          icon: Info,
          label: 'About Mag-Fi Navigator',
          description: 'Version 1.0.0 • Research Edition',
          action: 'navigate',
          color: 'text-cyan-400',
        },
      ],
    },
  ];

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950">
      {/* Header */}
      <div className="sticky top-0 z-20 bg-slate-900/95 backdrop-blur-md border-b border-cyan-500/20 p-4">
        <div className="flex items-center gap-4">
          <button
            onClick={() => navigate('/home')}
            className="p-2 rounded-xl bg-slate-800/80 border border-cyan-500/30 hover:bg-slate-700/80 transition-colors"
          >
            <ChevronLeft className="w-6 h-6 text-cyan-400" />
          </button>
          <h1 className="text-white font-semibold text-xl">Settings</h1>
        </div>
      </div>

      <div className="p-6 space-y-8 pb-24">
        {settingsSections.map((section, sectionIndex) => (
          <motion.div
            key={section.title}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: sectionIndex * 0.1 }}
          >
            <h2 className="text-cyan-300 text-sm font-medium mb-3">{section.title}</h2>
            <div className="bg-slate-800/50 border border-cyan-500/30 rounded-2xl overflow-hidden">
              {section.items.map((item, itemIndex) => (
                <div key={item.label}>
                  {item.action === 'navigate' && (
                    <button className="w-full flex items-center justify-between p-4 hover:bg-slate-800/70 transition-colors group">
                      <div className="flex items-center gap-3">
                        <div className="p-2 bg-slate-900/50 rounded-xl">
                          <item.icon className={`w-5 h-5 ${item.color}`} />
                        </div>
                        <div className="text-left">
                          <p className="text-white font-medium">{item.label}</p>
                          <p className="text-slate-400 text-sm mt-0.5">{item.description}</p>
                        </div>
                      </div>
                      <ChevronRight className="w-5 h-5 text-slate-600 group-hover:text-cyan-400 transition-colors" />
                    </button>
                  )}

                  {item.action === 'toggle' && (
                    <div className="flex items-center justify-between p-4">
                      <div className="flex items-center gap-3">
                        <div className="p-2 bg-slate-900/50 rounded-xl">
                          <item.icon className={`w-5 h-5 ${item.color}`} />
                        </div>
                        <div className="text-left">
                          <p className="text-white font-medium">{item.label}</p>
                          <p className="text-slate-400 text-sm mt-0.5">{item.description}</p>
                        </div>
                      </div>
                      <Switch
                        checked={item.state}
                        onCheckedChange={item.setState}
                      />
                    </div>
                  )}

                  {item.action === 'slider' && (
                    <div className="p-4">
                      <div className="flex items-center gap-3 mb-4">
                        <div className="p-2 bg-slate-900/50 rounded-xl">
                          <item.icon className={`w-5 h-5 ${item.color}`} />
                        </div>
                        <div className="flex-1">
                          <p className="text-white font-medium">{item.label}</p>
                          <p className="text-slate-400 text-sm mt-0.5">{item.description}</p>
                        </div>
                        <span className="text-cyan-400 font-semibold">{sensorSensitivity[0]}%</span>
                      </div>
                      <Slider
                        value={sensorSensitivity}
                        onValueChange={setSensorSensitivity}
                        max={100}
                        step={1}
                        className="w-full"
                      />
                    </div>
                  )}

                  {itemIndex < section.items.length - 1 && (
                    <div className="h-px bg-slate-700/50 mx-4" />
                  )}
                </div>
              ))}
            </div>
          </motion.div>
        ))}

        {/* App Info */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.5 }}
          className="bg-gradient-to-br from-cyan-900/20 to-blue-900/20 border border-cyan-500/30 rounded-2xl p-6 text-center"
        >
          <div className="inline-flex p-4 bg-cyan-500/20 rounded-2xl mb-4">
            <Compass className="w-8 h-8 text-cyan-400" />
          </div>
          <h3 className="text-white font-semibold text-lg mb-2">Mag-Fi Navigator</h3>
          <p className="text-cyan-400 text-sm mb-1">Adaptive Indoor Localization System</p>
          <p className="text-slate-400 text-xs">Version 1.0.0 • Research Edition</p>
          
          <div className="mt-6 pt-6 border-t border-cyan-500/20">
            <p className="text-slate-400 text-xs">
              Built for smart campuses and research environments
            </p>
            <p className="text-slate-500 text-xs mt-1">
              © 2026 Mag-Fi Navigator Team
            </p>
          </div>
        </motion.div>
      </div>
    </div>
  );
}
