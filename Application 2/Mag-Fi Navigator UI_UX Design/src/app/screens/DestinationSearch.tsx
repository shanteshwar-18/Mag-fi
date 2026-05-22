import { useState } from 'react';
import { useNavigate } from 'react-router';
import { motion } from 'motion/react';
import { Search, ChevronLeft, MapPin, Clock, ChevronRight, Beaker, GraduationCap, Briefcase, WashingMachine } from 'lucide-react';
import { Input } from '../components/ui/input';
import { Badge } from '../components/ui/badge';

export default function DestinationSearch() {
  const navigate = useNavigate();
  const [searchQuery, setSearchQuery] = useState('');

  const categories = [
    { name: 'Labs', icon: Beaker, color: 'bg-cyan-500/20 text-cyan-400 border-cyan-500/40' },
    { name: 'Classrooms', icon: GraduationCap, color: 'bg-blue-500/20 text-blue-400 border-blue-500/40' },
    { name: 'Offices', icon: Briefcase, color: 'bg-purple-500/20 text-purple-400 border-purple-500/40' },
    { name: 'Washrooms', icon: WashingMachine, color: 'bg-green-500/20 text-green-400 border-green-500/40' },
  ];

  const recentDestinations = [
    { name: 'Computer Lab 301', floor: 'Floor 3', distance: '45m' },
    { name: 'Library Reading Hall', floor: 'Floor 2', distance: '120m' },
    { name: 'Cafeteria', floor: 'Floor 1', distance: '200m' },
  ];

  const suggestedLocations = [
    { name: 'Electronics Lab 302', floor: 'Floor 3', distance: '30m', category: 'Lab' },
    { name: 'Classroom 304', floor: 'Floor 3', distance: '15m', category: 'Classroom' },
    { name: 'Dean Office', floor: 'Floor 4', distance: '85m', category: 'Office' },
    { name: 'Physics Lab 401', floor: 'Floor 4', distance: '95m', category: 'Lab' },
    { name: 'Auditorium', floor: 'Floor 1', distance: '180m', category: 'Hall' },
  ];

  const filteredLocations = searchQuery
    ? suggestedLocations.filter((loc) =>
        loc.name.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : suggestedLocations;

  const handleSelectDestination = (destination: string) => {
    navigate('/route', { state: { destination } });
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950">
      {/* Header */}
      <div className="sticky top-0 z-20 bg-slate-900/95 backdrop-blur-md border-b border-cyan-500/20 p-4">
        <div className="flex items-center gap-4 mb-4">
          <button
            onClick={() => navigate('/home')}
            className="p-2 rounded-xl bg-slate-800/80 border border-cyan-500/30 hover:bg-slate-700/80 transition-colors"
          >
            <ChevronLeft className="w-6 h-6 text-cyan-400" />
          </button>
          <h1 className="text-white font-semibold text-xl flex-1">Find Destination</h1>
        </div>

        {/* Search Bar */}
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className="relative"
        >
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-cyan-400" />
          <Input
            type="text"
            placeholder="Search classrooms, labs, offices..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-12 pr-4 py-6 bg-slate-800/80 border-cyan-500/30 text-white placeholder:text-slate-500 rounded-2xl focus:ring-2 focus:ring-cyan-500/50"
          />
        </motion.div>
      </div>

      <div className="p-6 space-y-6 pb-24">
        {/* Categories */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
        >
          <h2 className="text-cyan-300 text-sm font-medium mb-3">Categories</h2>
          <div className="grid grid-cols-2 gap-3">
            {categories.map((category, index) => (
              <motion.button
                key={category.name}
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ delay: 0.2 + index * 0.05 }}
                className={`p-4 rounded-2xl border ${category.color} hover:scale-105 transition-transform`}
              >
                <category.icon className="w-6 h-6 mb-2" />
                <p className="text-sm font-medium">{category.name}</p>
              </motion.button>
            ))}
          </div>
        </motion.div>

        {/* Recent Destinations */}
        {!searchQuery && (
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.3 }}
          >
            <div className="flex items-center gap-2 mb-3">
              <Clock className="w-4 h-4 text-cyan-400" />
              <h2 className="text-cyan-300 text-sm font-medium">Recent</h2>
            </div>
            <div className="space-y-2">
              {recentDestinations.map((dest, index) => (
                <motion.button
                  key={dest.name}
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.4 + index * 0.05 }}
                  onClick={() => handleSelectDestination(dest.name)}
                  className="w-full flex items-center justify-between p-4 bg-slate-800/50 border border-cyan-500/20 rounded-2xl hover:bg-slate-800 hover:border-cyan-500/40 transition-all group"
                >
                  <div className="flex items-center gap-3">
                    <div className="p-2 bg-cyan-500/20 rounded-xl">
                      <Clock className="w-5 h-5 text-cyan-400" />
                    </div>
                    <div className="text-left">
                      <p className="text-white font-medium">{dest.name}</p>
                      <p className="text-slate-400 text-sm mt-0.5">{dest.floor} • {dest.distance}</p>
                    </div>
                  </div>
                  <ChevronRight className="w-5 h-5 text-slate-600 group-hover:text-cyan-400 transition-colors" />
                </motion.button>
              ))}
            </div>
          </motion.div>
        )}

        {/* Suggested/Search Results */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: searchQuery ? 0 : 0.5 }}
        >
          <div className="flex items-center gap-2 mb-3">
            <MapPin className="w-4 h-4 text-cyan-400" />
            <h2 className="text-cyan-300 text-sm font-medium">
              {searchQuery ? 'Search Results' : 'Suggested Locations'}
            </h2>
          </div>
          <div className="space-y-2">
            {filteredLocations.length > 0 ? (
              filteredLocations.map((location, index) => (
                <motion.button
                  key={location.name}
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: searchQuery ? 0 : 0.6 + index * 0.05 }}
                  onClick={() => handleSelectDestination(location.name)}
                  className="w-full flex items-center justify-between p-4 bg-slate-800/50 border border-cyan-500/20 rounded-2xl hover:bg-slate-800 hover:border-cyan-500/40 transition-all group"
                >
                  <div className="flex items-center gap-3">
                    <div className="p-2 bg-blue-500/20 rounded-xl">
                      <MapPin className="w-5 h-5 text-blue-400" />
                    </div>
                    <div className="text-left">
                      <div className="flex items-center gap-2">
                        <p className="text-white font-medium">{location.name}</p>
                        <Badge className="bg-cyan-500/20 text-cyan-400 text-xs border-cyan-500/40">
                          {location.category}
                        </Badge>
                      </div>
                      <p className="text-slate-400 text-sm mt-0.5">
                        {location.floor} • {location.distance} away
                      </p>
                    </div>
                  </div>
                  <ChevronRight className="w-5 h-5 text-slate-600 group-hover:text-cyan-400 transition-colors" />
                </motion.button>
              ))
            ) : (
              <div className="text-center py-12">
                <Search className="w-12 h-12 text-slate-600 mx-auto mb-3" />
                <p className="text-slate-400">No locations found</p>
                <p className="text-slate-500 text-sm mt-1">Try a different search term</p>
              </div>
            )}
          </div>
        </motion.div>
      </div>
    </div>
  );
}
