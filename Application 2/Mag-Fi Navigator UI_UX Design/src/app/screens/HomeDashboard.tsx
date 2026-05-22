import { useNavigate } from 'react-router';
import { motion } from 'motion/react';
import {
  Navigation,
  Radio,
  Activity,
  MapPin,
  Battery,
  Wifi,
  Settings,
  ChevronRight,
  Compass,
} from 'lucide-react';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';

export default function HomeDashboard() {
  const navigate = useNavigate();

  const recentLocations = [
    { name: 'Computer Lab 301', time: '2 hours ago' },
    { name: 'Library Reading Hall', time: 'Yesterday' },
    { name: 'Cafeteria Floor 2', time: '2 days ago' },
  ];

  const sensorStatus = [
    { name: 'Magnetometer', status: 'Active', color: 'text-green-400', icon: Compass },
    { name: 'Accelerometer', status: 'Active', color: 'text-green-400', icon: Activity },
    { name: 'Wi-Fi Scanner', status: 'Active', color: 'text-green-400', icon: Wifi },
    { name: 'Gyroscope', status: 'Active', color: 'text-green-400', icon: Radio },
  ];

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950">
      {/* Header */}
      <div className="bg-gradient-to-r from-slate-900/50 to-blue-900/30 backdrop-blur-sm border-b border-cyan-500/20 p-6">
        <div className="flex items-center justify-between">
          <div>
            <motion.h1
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              className="text-2xl font-bold text-white"
            >
              Mag-Fi Navigator
            </motion.h1>
            <p className="text-cyan-400 text-sm mt-1">Indoor Positioning System</p>
          </div>
          <button
            onClick={() => navigate('/settings')}
            className="p-3 rounded-xl bg-slate-800/50 border border-cyan-500/30 hover:bg-slate-700/50 transition-colors"
          >
            <Settings className="w-6 h-6 text-cyan-400" />
          </button>
        </div>
      </div>

      <div className="p-6 space-y-6 pb-24">
        {/* Welcome Card */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
        >
          <Card className="bg-gradient-to-br from-cyan-600 to-blue-700 border-none shadow-lg shadow-cyan-500/20 p-6">
            <div className="flex items-center gap-4">
              <div className="p-4 bg-white/10 rounded-2xl backdrop-blur-sm">
                <MapPin className="w-8 h-8 text-white" />
              </div>
              <div className="flex-1">
                <h2 className="text-white text-xl font-semibold">Welcome Back!</h2>
                <p className="text-cyan-100 text-sm mt-1">Ready to navigate</p>
              </div>
            </div>
          </Card>
        </motion.div>

        {/* Current Location */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
        >
          <Card className="bg-slate-900/50 border-cyan-500/30 backdrop-blur-sm p-6">
            <h3 className="text-cyan-300 text-sm font-medium mb-3">Current Location</h3>
            <div className="flex items-center justify-between">
              <div>
                <p className="text-white text-lg font-semibold">Engineering Block A</p>
                <p className="text-slate-400 text-sm mt-1">Floor 3 • Zone B</p>
              </div>
              <div className="px-4 py-2 bg-green-500/20 border border-green-500/30 rounded-lg">
                <p className="text-green-400 text-xs font-medium">Offline Ready</p>
              </div>
            </div>
          </Card>
        </motion.div>

        {/* Main Actions */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3 }}
          className="grid grid-cols-2 gap-4"
        >
          <button
            onClick={() => navigate('/localization')}
            className="group relative overflow-hidden bg-gradient-to-br from-cyan-600 to-cyan-700 hover:from-cyan-500 hover:to-cyan-600 rounded-2xl p-6 transition-all duration-300 shadow-lg shadow-cyan-500/20 hover:shadow-cyan-500/40"
          >
            <div className="relative z-10">
              <Radio className="w-8 h-8 text-white mb-3" />
              <p className="text-white font-semibold text-left">Live Localization</p>
              <p className="text-cyan-100 text-xs mt-1 text-left">Track position</p>
            </div>
            <motion.div
              className="absolute inset-0 bg-gradient-to-r from-white/0 to-white/10"
              initial={{ x: '-100%' }}
              whileHover={{ x: '100%' }}
              transition={{ duration: 0.5 }}
            />
          </button>

          <button
            onClick={() => navigate('/search')}
            className="group relative overflow-hidden bg-gradient-to-br from-blue-600 to-blue-700 hover:from-blue-500 hover:to-blue-600 rounded-2xl p-6 transition-all duration-300 shadow-lg shadow-blue-500/20 hover:shadow-blue-500/40"
          >
            <div className="relative z-10">
              <Navigation className="w-8 h-8 text-white mb-3" />
              <p className="text-white font-semibold text-left">Start Navigation</p>
              <p className="text-blue-100 text-xs mt-1 text-left">Find destination</p>
            </div>
            <motion.div
              className="absolute inset-0 bg-gradient-to-r from-white/0 to-white/10"
              initial={{ x: '-100%' }}
              whileHover={{ x: '100%' }}
              transition={{ duration: 0.5 }}
            />
          </button>
        </motion.div>

        {/* Sensor Status */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4 }}
        >
          <Card className="bg-slate-900/50 border-cyan-500/30 backdrop-blur-sm p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-cyan-300 text-sm font-medium">Sensor Status</h3>
              <button
                onClick={() => navigate('/sensors')}
                className="text-cyan-400 text-xs hover:text-cyan-300 transition-colors"
              >
                View Details →
              </button>
            </div>
            <div className="space-y-3">
              {sensorStatus.map((sensor, index) => (
                <motion.div
                  key={sensor.name}
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.5 + index * 0.1 }}
                  className="flex items-center justify-between p-3 bg-slate-800/50 rounded-xl"
                >
                  <div className="flex items-center gap-3">
                    <sensor.icon className={`w-5 h-5 ${sensor.color}`} />
                    <span className="text-white text-sm">{sensor.name}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <div className="w-2 h-2 rounded-full bg-green-400 animate-pulse" />
                    <span className={`text-xs ${sensor.color}`}>{sensor.status}</span>
                  </div>
                </motion.div>
              ))}
            </div>
          </Card>
        </motion.div>

        {/* Recent Locations */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.5 }}
        >
          <Card className="bg-slate-900/50 border-cyan-500/30 backdrop-blur-sm p-6">
            <h3 className="text-cyan-300 text-sm font-medium mb-4">Recent Locations</h3>
            <div className="space-y-3">
              {recentLocations.map((location, index) => (
                <motion.button
                  key={location.name}
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.6 + index * 0.1 }}
                  className="w-full flex items-center justify-between p-3 bg-slate-800/50 rounded-xl hover:bg-slate-800 transition-colors group"
                >
                  <div className="flex items-center gap-3">
                    <MapPin className="w-5 h-5 text-cyan-400" />
                    <div className="text-left">
                      <p className="text-white text-sm">{location.name}</p>
                      <p className="text-slate-400 text-xs mt-0.5">{location.time}</p>
                    </div>
                  </div>
                  <ChevronRight className="w-5 h-5 text-slate-600 group-hover:text-cyan-400 transition-colors" />
                </motion.button>
              ))}
            </div>
          </Card>
        </motion.div>

        {/* Battery Status */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.6 }}
        >
          <Card className="bg-gradient-to-r from-green-900/30 to-emerald-900/30 border-green-500/30 p-4">
            <div className="flex items-center gap-3">
              <Battery className="w-5 h-5 text-green-400" />
              <div className="flex-1">
                <p className="text-green-300 text-sm font-medium">Battery Efficient Mode</p>
                <p className="text-green-400/70 text-xs mt-0.5">Offline maps loaded • Low power consumption</p>
              </div>
            </div>
          </Card>
        </motion.div>
      </div>
    </div>
  );
}
