package com.thesisapp.presentation.adapters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.thesisapp.R
import com.thesisapp.data.non_dao.Team
import com.thesisapp.data.repository.TeamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class TeamSwitcherAdapter(
    context: Context,
    private val teams: List<Team>,
    private val teamRepository: TeamRepository,
    private val scope: LifecycleCoroutineScope
) : ArrayAdapter<Team>(context, 0, teams) {

    private val inflater = LayoutInflater.from(context)
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_team_switcher_row, parent, false)

        val imgLogo = view.findViewById<ImageView>(R.id.imgLogo)
        val txtName = view.findViewById<TextView>(R.id.txtName)

        val team = teams[position]
        txtName.text = team.name

        val logoPath = team.logoPath
        if (logoPath.isNullOrBlank()) {
            imgLogo.setImageResource(R.drawable.ic_logo)
            imgLogo.tag = null
            return view
        }

        val cached = bitmapCache[logoPath]
        if (cached != null) {
            imgLogo.setImageBitmap(cached)
            imgLogo.tag = logoPath
            return view
        }

        imgLogo.setImageResource(R.drawable.ic_logo)
        imgLogo.tag = logoPath

        scope.launch(Dispatchers.IO) {
            runCatching {
                val signedUrl = teamRepository.getTeamLogoSignedUrl(logoPath)
                val bytes = URL(signedUrl).openStream().use { it.readBytes() }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) {
                    bitmapCache[logoPath] = bmp
                }
                bmp
            }.onSuccess { bmp ->
                if (bmp != null) {
                    withContext(Dispatchers.Main) {
                        if (imgLogo.tag == logoPath) {
                            imgLogo.setImageBitmap(bmp)
                        }
                    }
                }
            }
        }

        return view
    }
}
