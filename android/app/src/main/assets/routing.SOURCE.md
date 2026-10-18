`routing.dat.gz` is the routing data file of the ReFlux core
(`cmd/routing-data` in github.com/BeleBob/ReFlux), built from:

- runetfreedom/russia-v2ray-rules-dat (geosite.dat, geoip.dat), which
  itself combines v2fly/domain-list-community, antifilter and other lists;
- ancored/ru-mobile-whitelist (the mobile internet whitelists), rebuilt
  from hxehex/russia-mobile-internet-whitelist and other public lists.

The app replaces it with the copy ReFlux rebuilds daily (the routing-data
release) about once a week.
