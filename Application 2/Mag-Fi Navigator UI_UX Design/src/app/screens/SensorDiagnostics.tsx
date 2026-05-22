import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router';
import { motion } from 'motion/react';
import {
  ChevronLeft,
  Activity,
  Radio,
  Compass,
  Wifi,
  Signal,
  TrendingUp,
  Gauge,
} from 'lucide-react';
import { LineChart, Line, ResponsiveContainer, XAxis, YAxis } from 'recharts';

export default function SensorDiagnostics() {
  const navigate = useNavigate();

  // Sensor data state
  const [accelData, setAccelData] = useState({ x: 0, y: 0, z: 9.8 });
  const [magnetData, setMagnetData] = useState({ x: 23.5, y: -12.3, z: 45.6 });
  const [gyroData, setGyroData] = useState({ x: 0.02, y: -0.01, z: 0.05 });
  const [wifiRSSI, setWifiRSSI] = useState(-45);
  const [heading, setHeading] = useState(135);

  // Chart data
  const [chartData, setChartData] = useState(
    Array.from({ length: 20 }, (_, i) => ({
      time: i,
      accel: 9.8 + (Math.random() - 0.5) * 0.5,
      magnet: 45 + (Math.random() - 0.5) * 2,
      gyro: (Math.random() - 0.5) * 0.1,
    }))
  );

  // Simulate live sensor updates
  useEffect(() => {
    const interval = setInterval(() => {
      setAccelData({
        x: (Math.random() - 0.5) * 2,
        y: (Math.random() - 0.5) * 2,
        z: 9.8 + (Math.random() - 0.5) * 0.5,
      });

      setMagnetData({
        x: 23.5 + (Math.random() - 0.5) * 5,
        y: -12.3 + (Math.random() - 0.5) * 5,
        z: 45.6 + (Math.random() - 0.5) * 5,
      });

      setGyroData({
        x: (Math.random() - 0.5) * 0.1,
        y: (Math.random() - 0.5) * 0.1,
        z: (Math.random() - 0.5) * 0.1,
      });

      setWifiRSSI(-45 + (Math.random() - 0.5) * 10);
      setHeading((prev) => (prev + (Math.random() - 0.5) * 5 + 360) % 360);

      // Update chart data
      setChartData((prev) => {
        const newData = [...prev.slice(1), {
          time: prev[prev.length - 1].time + 1,
          accel: 9.8 + (Math.random() - 0.5) * 0.5,
          magnet: 45 + (Math.random() - 0.5) * 2,
          gyro: (Math.random() - 0.5) * 0.1,
        }];
        return newData;
      });
    }, 500);

    return () => clearInterval(interval);
  }, []);

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
          <div className="flex-1">
            <h1 className="text-white font-semibold text-xl">Sensor Diagnostics</h1>
            <p className="text-cyan-400 text-sm">Real-time sensor monitoring</p>
          </div>
          <div className="w-2 h-2 rounded-full bg-green-400 animate-pulse" />
        </div>
      </div>

      <div className="p-6 space-y-6 pb-24">
        {/* Heading Compass */}
        <motion.div
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          className="bg-gradient-to-br from-slate-800/80 to-slate-900/80 border border-cyan-500/30 rounded-3xl p-8 shadow-lg shadow-cyan-500/10"
        >
          <div className="flex items-center gap-2 mb-6">
            <Compass className="w-5 h-5 text-cyan-400" />
            <h3 className="text-cyan-300 font-medium">Heading Compass</h3>
          </div>

          <div className="flex items-center justify-center">
            <div className="relative w-48 h-48">
              {/* Compass Circle */}
              <svg className="w-full h-full" viewBox="0 0 200 200">
                <circle cx="100" cy="100" r="90" fill="none" stroke="#1e293b" strokeWidth="2" />
                <circle cx="100" cy="100" r="85" fill="none" stroke="#0ea5e9" strokeWidth="1" opacity="0.3" />

                {/* Cardinal Directions */}
                <text x="100" y="25" fill="#06b6d4" fontSize="16" fontWeight="bold" textAnchor="middle">N</text>
                <text x="175" y="105" fill="#475569" fontSize="14" textAnchor="middle">E</text>
                <text x="100" y="185" fill="#475569" fontSize="14" textAnchor="middle">S</text>
                <text x="25" y="105" fill="#475569" fontSize="14" textAnchor="middle">W</text>

                {/* Rotating Needle */}
                <motion.g
                  animate={{ rotate: heading }}
                  transition={{ type: 'spring', stiffness: 100, damping: 20 }}
                  style={{ transformOrigin: '100px 100px' }}
                >
                  <path d="M 100 40 L 105 100 L 100 95 L 95 100 Z" fill="#ef4444" />
                  <path d="M 100 160 L 105 100 L 100 105 L 95 100 Z" fill="#64748b" />
                </motion.g>

                {/* Center Dot */}
                <circle cx="100" cy="100" r="5" fill="#0ea5e9" />
              </svg>
            </div>
          </div>

          <div className="text-center mt-4">
            <p className="text-white text-4xl font-bold">{Math.round(heading)}°</p>
            <p className="text-cyan-400 text-sm mt-1">
              {heading >= 0 && heading < 45 && 'North'}
              {heading >= 45 && heading < 90 && 'North-East'}
              {heading >= 90 && heading < 135 && 'East'}
              {heading >= 135 && heading < 180 && 'South-East'}
              {heading >= 180 && heading < 225 && 'South'}
              {heading >= 225 && heading < 270 && 'South-West'}
              {heading >= 270 && heading < 315 && 'West'}
              {heading >= 315 && 'North-West'}
            </p>
          </div>
        </motion.div>

        {/* Accelerometer */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="bg-slate-800/50 border border-cyan-500/30 rounded-2xl p-6"
        >
          <div className="flex items-center gap-2 mb-4">
            <Activity className="w-5 h-5 text-cyan-400" />
            <h3 className="text-cyan-300 font-medium">Accelerometer</h3>
            <div className="ml-auto w-2 h-2 rounded-full bg-green-400 animate-pulse" />
          </div>

          <div className="grid grid-cols-3 gap-4 mb-4">
            <div className="bg-slate-900/50 border border-cyan-500/20 rounded-xl p-3">
              <p className="text-cyan-400 text-xs mb-1">X-Axis</p>
              <p className="text-white text-xl font-bold">{accelData.x.toFixed(2)}</p>
              <p className="text-slate-500 text-xs">m/s²</p>
            </div>
            <div className="bg-slate-900/50 border border-cyan-500/20 rounded-xl p-3">
              <p className="text-cyan-400 text-xs mb-1">Y-Axis</p>
              <p className="text-white text-xl font-bold">{accelData.y.toFixed(2)}</p>
              <p className="text-slate-500 text-xs">m/s²</p>
            </div>
            <div className="bg-slate-900/50 border border-cyan-500/20 rounded-xl p-3">
              <p className="text-cyan-400 text-xs mb-1">Z-Axis</p>
              <p className="text-white text-xl font-bold">{accelData.z.toFixed(2)}</p>
              <p className="text-slate-500 text-xs">m/s²</p>
            </div>
          </div>

          {/* Mini Chart */}
          <div className="h-24 bg-slate-900/30 rounded-xl overflow-hidden">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData}>
                <Line type="monotone" dataKey="accel" stroke="#06b6d4" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </motion.div>

        {/* Magnetometer */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="bg-slate-800/50 border border-cyan-500/30 rounded-2xl p-6"
        >
          <div className="flex items-center gap-2 mb-4">
            <Radio className="w-5 h-5 text-cyan-400" />
            <h3 className="text-cyan-300 font-medium">Magnetometer</h3>
            <div className="ml-auto w-2 h-2 rounded-full bg-green-400 animate-pulse" />
          </div>

          <div className="grid grid-cols-3 gap-4 mb-4">
            <div className="bg-slate-900/50 border border-purple-500/20 rounded-xl p-3">
              <p className="text-purple-400 text-xs mb-1">X-Axis</p>
              <p className="text-white text-xl font-bold">{magnetData.x.toFixed(1)}</p>
              <p className="text-slate-500 text-xs">μT</p>
            </div>
            <div className="bg-slate-900/50 border border-purple-500/20 rounded-xl p-3">
              <p className="text-purple-400 text-xs mb-1">Y-Axis</p>
              <p className="text-white text-xl font-bold">{magnetData.y.toFixed(1)}</p>
              <p className="text-slate-500 text-xs">μT</p>
            </div>
            <div className="bg-slate-900/50 border border-purple-500/20 rounded-xl p-3">
              <p className="text-purple-400 text-xs mb-1">Z-Axis</p>
              <p className="text-white text-xl font-bold">{magnetData.z.toFixed(1)}</p>
              <p className="text-slate-500 text-xs">μT</p>
            </div>
          </div>

          {/* Mini Chart */}
          <div className="h-24 bg-slate-900/30 rounded-xl overflow-hidden">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData}>
                <Line type="monotone" dataKey="magnet" stroke="#a855f7" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </motion.div>

        {/* Gyroscope */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3 }}
          className="bg-slate-800/50 border border-cyan-500/30 rounded-2xl p-6"
        >
          <div className="flex items-center gap-2 mb-4">
            <Gauge className="w-5 h-5 text-cyan-400" />
            <h3 className="text-cyan-300 font-medium">Gyroscope</h3>
            <div className="ml-auto w-2 h-2 rounded-full bg-green-400 animate-pulse" />
          </div>

          <div className="grid grid-cols-3 gap-4 mb-4">
            <div className="bg-slate-900/50 border border-blue-500/20 rounded-xl p-3">
              <p className="text-blue-400 text-xs mb-1">X-Axis</p>
              <p className="text-white text-xl font-bold">{gyroData.x.toFixed(3)}</p>
              <p className="text-slate-500 text-xs">rad/s</p>
            </div>
            <div className="bg-slate-900/50 border border-blue-500/20 rounded-xl p-3">
              <p className="text-blue-400 text-xs mb-1">Y-Axis</p>
              <p className="text-white text-xl font-bold">{gyroData.y.toFixed(3)}</p>
              <p className="text-slate-500 text-xs">rad/s</p>
            </div>
            <div className="bg-slate-900/50 border border-blue-500/20 rounded-xl p-3">
              <p className="text-blue-400 text-xs mb-1">Z-Axis</p>
              <p className="text-white text-xl font-bold">{gyroData.z.toFixed(3)}</p>
              <p className="text-slate-500 text-xs">rad/s</p>
            </div>
          </div>

          {/* Mini Chart */}
          <div className="h-24 bg-slate-900/30 rounded-xl overflow-hidden">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData}>
                <Line type="monotone" dataKey="gyro" stroke="#3b82f6" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </motion.div>

        {/* Wi-Fi RSSI */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4 }}
          className="bg-slate-800/50 border border-cyan-500/30 rounded-2xl p-6"
        >
          <div className="flex items-center gap-2 mb-4">
            <Wifi className="w-5 h-5 text-cyan-400" />
            <h3 className="text-cyan-300 font-medium">Wi-Fi RSSI</h3>
            <div className="ml-auto w-2 h-2 rounded-full bg-green-400 animate-pulse" />
          </div>

          <div className="flex items-center justify-between mb-4">
            <div className="flex-1">
              <p className="text-white text-4xl font-bold">{Math.round(wifiRSSI)} dBm</p>
              <p className="text-cyan-400 text-sm mt-1">
                {wifiRSSI > -50 && 'Excellent'}
                {wifiRSSI <= -50 && wifiRSSI > -60 && 'Good'}
                {wifiRSSI <= -60 && wifiRSSI > -70 && 'Fair'}
                {wifiRSSI <= -70 && 'Weak'}
              </p>
            </div>

            {/* Signal Bars */}
            <div className="flex items-end gap-1.5 h-16">
              {[1, 2, 3, 4, 5].map((bar) => (
                <div
                  key={bar}
                  className={`w-4 rounded-sm transition-all ${
                    wifiRSSI > -70 + (5 - bar) * 5
                      ? 'bg-gradient-to-t from-cyan-500 to-cyan-400'
                      : 'bg-slate-700'
                  }`}
                  style={{ height: `${bar * 20}%` }}
                />
              ))}
            </div>
          </div>

          {/* Access Points */}
          <div className="bg-slate-900/50 border border-cyan-500/20 rounded-xl p-4">
            <p className="text-cyan-400 text-xs mb-3">Detected Access Points</p>
            <div className="space-y-2">
              {['AP-3B-01', 'AP-3B-02', 'AP-3B-03'].map((ap, index) => (
                <div key={ap} className="flex items-center justify-between">
                  <span className="text-white text-sm">{ap}</span>
                  <span className="text-slate-400 text-xs">
                    {Math.round(wifiRSSI - index * 5)} dBm
                  </span>
                </div>
              ))}
            </div>
          </div>
        </motion.div>
      </div>
    </div>
  );
}
